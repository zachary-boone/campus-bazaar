package com.campus.bazaar.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.dto.ScrollResult;
import com.campus.bazaar.entity.Follow;
import com.campus.bazaar.entity.Post;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.mapper.FollowMapper;
import com.campus.bazaar.mapper.PostMapper;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.service.IPostService;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.SystemConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 帖子服务 - ZSet 点赞排行 + Feed 关注流
 * <p>
 * 点赞数据用 Redis ZSet 存储（post:liked:{postId}，member=userId，score=点赞时间戳）：
 * - 点赞：ZADD；取消：ZREM；是否点赞：ZSCORE；
 * - 点赞排行：ZREVRANGE 按点赞时间倒序取用户列表；
 * - 帖子点赞数字段（liked）与 DB 同步自增/自减，保证热点查询走内存、数据落库。
 * <p>
 * Feed 关注流（推模式）：
 * - 发帖时把 postId 写入每个粉丝的收件箱 ZSet（feed:{userId}，score=发帖时间戳）；
 * - 读流用 ZREVRANGEBYSCORE 滚动分页（游标 lastId + offset 处理同毫秒多条），
 *   相比 offset 深分页，数据量大时性能稳定。
 */
@Slf4j
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements IPostService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private FollowMapper followMapper;

    /** 关注流每次拉取条数 */
    private static final int FEED_PAGE_SIZE = 5;

    @Override
    public Result likePost(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        String key = RedisConstants.POST_LIKED_KEY + id;

        // 1. 判断是否已点赞（ZSet 里有 score 说明点过）
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        boolean liked = score != null;

        if (!liked) {
            // 2a. 点赞：ZADD score=当前时间戳，DB liked+1
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            update().setSql("liked = liked + 1").eq("id", id).update();
        } else {
            // 2b. 取消：ZREM，DB liked-1
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            update().setSql("liked = liked - 1").eq("id", id).update();
        }
        return Result.ok();
    }

    @Override
    public boolean isLiked(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return false;
        }
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.POST_LIKED_KEY + id, userId.toString());
        return score != null;
    }

    @Override
    public List<Post> queryPostLikes(Long id, Integer top) {
        // 1. ZREVRANGE 取点赞时间最近的 top 个用户
        Set<String> userIds = stringRedisTemplate.opsForZSet().reverseRange(RedisConstants.POST_LIKED_KEY + id, 0, (top == null ? 5 : top) - 1);
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 批量查用户信息，转成 Post 视图（只带用户字段，前端复用帖子卡片）
        List<Long> ids = userIds.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(ids);
        if (users == null) {
            return new ArrayList<>();
        }
        return users.stream().map(u -> {
            Post post = new Post();
            post.setId(id);
            post.setUserId(u.getId());
            post.setName(u.getNickName());
            post.setIcon(u.getIcon());
            return post;
        }).collect(Collectors.toList());
    }

    @Override
    public Result savePostWithFeed(Post post) {
        // 1. 保存帖子
        save(post);
        Long postId = post.getId();
        Long authorId = post.getUserId();

        // 2. 推模式：把 postId 写入每个粉丝的收件箱（feed:{followerId}）
        //    注：本项目为单机演示，直接遍历粉丝推送；粉丝量大的用户可切换拉模式/混合模式
        List<Follow> fans = followMapper.selectList(
                new QueryWrapper<Follow>().eq("follow_user_id", authorId));
        long now = System.currentTimeMillis();
        for (Follow fan : fans) {
            stringRedisTemplate.opsForZSet()
                    .add(RedisConstants.FEED_KEY + fan.getUserId(), postId.toString(), now);
        }
        log.info("[Feed] 帖子 {} 已推送给 {} 个粉丝", postId, fans.size());
        return Result.ok(postId);
    }

    @Override
    public Result queryFollowFeed(Long lastId, Integer offset) {
        Long userId = UserHolder.getUserId();
        String key = RedisConstants.FEED_KEY + userId;

        // 1. 滚动分页：ZREVRANGEBYSCORE key (max, min] limit offset count
        long max = (lastId == null || lastId <= 0) ? System.currentTimeMillis() : lastId;
        int off = (offset == null || offset < 0) ? 0 : offset;
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, off, FEED_PAGE_SIZE);
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok(new ScrollResult());
        }

        // 2. 收集 postId（按时间降序），同时算出 minTime 与同分偏移
        List<Long> postIds = new ArrayList<>();
        long minTime = 0L;
        int sameScoreOffset = 0;
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            postIds.add(Long.valueOf(t.getValue()));
            long score = t.getScore().longValue();
            if (minTime == 0L) {
                minTime = score;
            }
            if (score == minTime) {
                sameScoreOffset++;
            }
        }

        // 3. 批量查帖子并保持 ZSet 顺序
        List<Post> posts = listByIds(postIds);
        Map<Long, Post> postMap = posts.stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        List<Post> ordered = postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 4. 填充作者信息 + 当前用户是否点赞
        ordered.forEach(post -> {
            User author = userMapper.selectById(post.getUserId());
            if (author != null) {
                post.setName(author.getNickName());
                post.setIcon(author.getIcon());
            }
            post.setIsLike(isLiked(post.getId()));
        });

        // 5. 返回滚动游标
        ScrollResult scroll = new ScrollResult();
        scroll.setList(ordered);
        scroll.setMinTime(minTime);
        scroll.setOffset(sameScoreOffset);
        return Result.ok(scroll);
    }
}
