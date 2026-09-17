package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.dto.UserCardDTO;
import com.campus.bazaar.dto.UserDTO;
import com.campus.bazaar.entity.Follow;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.entity.UserInfo;
import com.campus.bazaar.mapper.FollowMapper;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.service.IFollowService;
import com.campus.bazaar.service.IUserInfoService;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注关系
 * <p>
 * 存储设计：
 * <ul>
 *   <li>DB {@code tb_follow} 是唯一真相（uk_user_follow 唯一键兜底，保证一人只关注一次），
 *       关注数/粉丝数都用 COUNT 实时算，不做冗余计数，避免双写漂移。</li>
 *   <li>Redis Set {@code follows:{userId}} 只作为"共同关注"求交集的加速结构；
 *       它是派生数据，缺失/被清时从 DB 自愈重建（见 {@link #followedIds}）。</li>
 * </ul>
 */
@Slf4j
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    /** 关注关系 Set：follows:{userId} —— 该用户关注的人，仅用于共同关注求交集 */
    private static final String FOLLOWS_KEY = "follows:";

    /** 列表单次返回上限：避免一次把全表捞出来（校园场景量小，暂不做游标分页） */
    private static final int MAX_LIST_SIZE = 200;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private IUserInfoService userInfoService;

    // ==================================================================
    //  关注 / 取关
    // ==================================================================

    @Override
    @Transactional
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (followUserId == null) {
            return Result.fail("参数不合法");
        }
        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }

        String key = FOLLOWS_KEY + userId;

        if (Boolean.TRUE.equals(isFollow)) {
            // 幂等：已关注则直接返回计数，不重复插入、也不报错
            // （前端重复点击、网络重试都会走到这里，DB 的 uk_user_follow 是最后一道防线）
            Integer exists = query().eq("user_id", userId)
                    .eq("follow_user_id", followUserId).count();
            if (exists == null || exists == 0) {
                // 目标用户必须存在，避免关注到已注销/伪造的 id
                if (userMapper.selectById(followUserId) == null) {
                    return Result.fail("用户不存在");
                }
                save(new Follow().setUserId(userId).setFollowUserId(followUserId));
            }
            stringRedisTemplate.opsForSet().add(key, String.valueOf(followUserId));
        } else {
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            stringRedisTemplate.opsForSet().remove(key, String.valueOf(followUserId));
        }

        // 直接回传最新计数，前端不用再发一次请求
        return Result.ok(counts(userId));
    }

    @Override
    public Boolean isFollow(Long followUserId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return false;
        }
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId).count();
        return count != null && count > 0;
    }

    // ==================================================================
    //  计数
    // ==================================================================

    @Override
    public long countFollowers(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return query().eq("follow_user_id", userId).count();
    }

    @Override
    public long countFollowing(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return query().eq("user_id", userId).count();
    }

    @Override
    public Result countResult(Long userId) {
        if (userId == null) {
            return Result.fail("参数不合法");
        }
        return Result.ok(counts(userId));
    }

    @Override
    public Map<Long, Long> countFollowersBatch(List<Long> userIds) {
        Map<Long, Long> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        // 一次 GROUP BY 取回所有人的粉丝数（没粉丝的用户不会出现在结果里，调用方按 0 兜底）
        QueryWrapper<Follow> wrapper = new QueryWrapper<>();
        wrapper.select("follow_user_id", "COUNT(*) AS fans")
                .in("follow_user_id", userIds)
                .groupBy("follow_user_id");
        List<Map<String, Object>> rows = baseMapper.selectMaps(wrapper);
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            Object id = row.get("follow_user_id");
            Object fans = row.get("fans");
            if (id instanceof Number) {
                result.put(((Number) id).longValue(),
                        fans instanceof Number ? ((Number) fans).longValue() : 0L);
            }
        }
        return result;
    }

    private Map<String, Long> counts(Long userId) {
        Map<String, Long> data = new LinkedHashMap<>();
        data.put("followers", countFollowers(userId));
        data.put("following", countFollowing(userId));
        return data;
    }

    // ==================================================================
    //  关注 / 粉丝列表
    // ==================================================================

    @Override
    public Result followingList(Long userId) {
        // tb_follow.user_id = userId 的行，对端是 follow_user_id
        return relationUsers(userId, true);
    }

    @Override
    public Result followerList(Long userId) {
        // tb_follow.follow_user_id = userId 的行，对端是 user_id
        return relationUsers(userId, false);
    }

    /**
     * 组装"某用户的关注/粉丝"列表为 {@link UserCardDTO}
     * @param userId    目标用户
     * @param following true=查 TA 关注的人；false=查 TA 的粉丝
     */
    private Result relationUsers(Long userId, boolean following) {
        if (userId == null) {
            return Result.fail("参数不合法");
        }
        String queryColumn = following ? "user_id" : "follow_user_id";
        String resultColumn = following ? "follow_user_id" : "user_id";

        // 1. 取关联关系（最近关注/被关注的排前面）
        List<Follow> relations = query()
                .eq(queryColumn, userId)
                .orderByDesc("create_time", "id")
                .last("LIMIT " + MAX_LIST_SIZE)
                .list();
        if (relations == null || relations.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2. 保持时间倒序：先按关系表顺序取 id，再按该顺序回填用户信息
        List<Long> ids = new ArrayList<>();
        for (Follow relation : relations) {
            Long id = following ? relation.getFollowUserId() : relation.getUserId();
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<User> users = userMapper.selectBatchIds(ids);
        if (users == null || users.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        // 3. 签名：一次批量查 tb_user_info
        List<UserInfo> infos = userInfoService.listByIds(ids);
        Map<Long, String> introduceMap = new LinkedHashMap<>();
        if (infos != null) {
            for (UserInfo info : infos) {
                introduceMap.put(info.getUserId(), info.getIntroduce());
            }
        }

        // 4. 当前登录用户关注了这批人里的哪些（一次 IN 查询，避免逐条 isFollow）
        Set<Long> iFollowed = followedIds(UserHolder.getUserId(), ids);

        // 5. 按 ids 顺序组装（顺带过滤掉用户已注销的脏数据）
        List<UserCardDTO> cards = new ArrayList<>();
        for (Long id : ids) {
            User user = userMap.get(id);
            if (user == null) {
                continue;
            }
            cards.add(toCard(user, introduceMap.get(id), iFollowed.contains(id)));
        }
        return Result.ok(cards);
    }

    // ==================================================================
    //  共同关注
    // ==================================================================

    @Override
    public Result followCommons(Long userId) {
        Long currentUserId = UserHolder.getUserId();
        if (currentUserId == null || userId == null) {
            return Result.ok(Collections.emptyList());
        }

        // 1. Redis Set 求交集（缺缓存时会先从 DB 自愈重建）
        Set<String> mine = followedSet(currentUserId);
        Set<String> theirs = followedSet(userId);
        if (mine.isEmpty() || theirs.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> commonIds = mine.stream()
                .filter(theirs::contains)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        if (commonIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2. 查用户信息
        List<User> users = userMapper.selectBatchIds(commonIds);
        if (users == null || users.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> dtos = users.stream().map(user -> {
            UserDTO dto = new UserDTO();
            dto.setId(user.getId());
            dto.setNickName(user.getNickName());
            dto.setIcon(user.getIcon());
            return dto;
        }).collect(Collectors.toList());
        return Result.ok(dtos);
    }

    /**
     * 取某用户的关注集合（Set 语义）
     * <p>
     * Redis 只是加速层，可能为空（被清、或关注数据是接口上线前手工写进 DB 的），
     * 因此这里做<b>自愈</b>：Set 为空时用 DB 重建，避免"共同关注"永远算出空集。
     */
    private Set<String> followedSet(Long userId) {
        Set<String> cached = stringRedisTemplate.opsForSet().members(FOLLOWS_KEY + userId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<Follow> relations = query().eq("user_id", userId)
                .last("LIMIT " + MAX_LIST_SIZE).list();
        if (relations == null || relations.isEmpty()) {
            return Collections.emptySet();
        }
        String[] ids = relations.stream()
                .map(relation -> String.valueOf(relation.getFollowUserId()))
                .toArray(String[]::new);
        stringRedisTemplate.opsForSet().add(FOLLOWS_KEY + userId, ids);
        log.info("[Follow] Redis 关注集合缺失，已从 DB 重建: userId={}, size={}", userId, ids.length);
        return new HashSet<>(java.util.Arrays.asList(ids));
    }

    /**
     * 在当前登录用户关注的人里，与 candidateIds 的交集（未登录返回空集）
     * 用一次 IN 查询代替 N 次 isFollow
     */
    private Set<Long> followedIds(Long currentUserId, List<Long> candidateIds) {
        if (currentUserId == null || candidateIds == null || candidateIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Follow> mine = query()
                .eq("user_id", currentUserId)
                .in("follow_user_id", candidateIds)
                .list();
        if (mine == null || mine.isEmpty()) {
            return Collections.emptySet();
        }
        return mine.stream().map(Follow::getFollowUserId).collect(Collectors.toSet());
    }

    // ==================================================================
    //  工具
    // ==================================================================

    private UserCardDTO toCard(User user, String introduce, boolean followed) {
        UserCardDTO card = new UserCardDTO();
        card.setId(user.getId());
        card.setNickName(user.getNickName());
        card.setIcon(user.getIcon());
        card.setIntroduce(introduce);
        card.setFollowed(followed);
        return card;
    }
}
