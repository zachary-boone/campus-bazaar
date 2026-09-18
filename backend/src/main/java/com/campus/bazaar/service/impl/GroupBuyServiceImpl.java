package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.GroupCardDTO;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.entity.GroupBuy;
import com.campus.bazaar.entity.GroupMember;
import com.campus.bazaar.mapper.GoodsMapper;
import com.campus.bazaar.mapper.GroupBuyMapper;
import com.campus.bazaar.mapper.GroupMemberMapper;
import com.campus.bazaar.service.IGroupBuyService;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 拼单服务
 * <p>
 * 并发与一致性设计（三层防护）：
 * <ol>
 *   <li><b>Redisson 分布式锁</b> {@code lock:group:join:{groupId}}：同一拼单的参团操作串行化，
 *       避免"两人同时看到最后一个名额都挤进来"的超员竞态；</li>
 *   <li><b>唯一键 uk_group_user(group_id, user_id)</b>：DB 最后一道防线，一人一团单只一条，
 *       并发重复参团抛 DuplicateKeyException 后按业务失败返回，不会出现重复成员；</li>
 *   <li><b>参团路径不用事务</b>（各语句自动提交）：锁内 insert 立即可见，
 *       后续抢锁者能读到最新的成员数；开团路径（拼单 + 团长成员两条插入）用事务保证原子。</li>
 * </ol>
 * 过期处理：定时任务每 30s 批量置 3 + 所有读路径先懒惰过期，双保险。
 */
@Slf4j
@Service
public class GroupBuyServiceImpl extends ServiceImpl<GroupBuyMapper, GroupBuy> implements IGroupBuyService {

    /** 参团锁前缀 */
    private static final String JOIN_LOCK_KEY = "lock:group:join:";

    /** 成团人数：校园二手"同班一起收"场景，发起人 + 1 人即可成团 */
    private static final int REQUIRED_NUM = 2;

    /** 拼单有效期（小时） */
    private static final int VALID_HOURS = 24;

    /** 拼单折扣：9 折 */
    private static final double GROUP_DISCOUNT = 0.9;

    @Resource
    private GroupMemberMapper groupMemberMapper;

    @Resource
    private GoodsMapper goodsMapper;

    @Resource
    private RedissonClient redissonClient;

    // ==================== 查询 ====================

    @Override
    public Result activeGroups() {
        expireOverdue();
        List<GroupBuy> groups = query().eq("status", GroupBuy.STATUS_ONGOING)
                .orderByDesc("create_time").list();
        // 商品已售出/下架的团不再展示（商品没了，拼单无意义）
        return Result.ok(toCards(filterSellable(groups), null));
    }

    /** 过滤掉商品非在售（status != 1）的拼单 */
    private List<GroupBuy> filterSellable(List<GroupBuy> groups) {
        if (groups.isEmpty()) {
            return groups;
        }
        List<Long> goodsIds = groups.stream().map(GroupBuy::getGoodsId).distinct().collect(Collectors.toList());
        Set<Long> sellable = goodsMapper.selectBatchIds(goodsIds).stream()
                .filter(g -> g.getStatus() != null && g.getStatus() == 1)
                .map(Goods::getId).collect(Collectors.toSet());
        return groups.stream().filter(g -> sellable.contains(g.getGoodsId())).collect(Collectors.toList());
    }

    @Override
    public Result activeByGoods(Long goodsId) {
        expireOverdue();
        // 该商品最近的拼单（含已成团/已过期）：详情页据此展示真实状态，
        // 避免"参团成团后按钮默默变回发起拼单"的状态不清
        List<GroupBuy> groups = query().eq("goods_id", goodsId)
                .orderByDesc("create_time")
                .last("LIMIT 5").list();
        // 带上当前用户视角：详情页据此显示"我发起的/我参加的"并隐藏参团按钮（未登录为 null）
        return Result.ok(toCards(groups, UserHolder.getUserId()));
    }

    @Override
    public Result myGroups() {
        Long userId = UserHolder.getUserId();
        // 我参加过的所有团（含团长身份：开团时也会写一条成员记录）
        List<GroupMember> memberships = groupMemberMapper.selectList(
                new QueryWrapper<GroupMember>().eq("user_id", userId).orderByDesc("create_time"));
        if (memberships.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> groupIds = memberships.stream().map(GroupMember::getGroupId).collect(Collectors.toList());
        List<GroupBuy> groups = listByIds(groupIds)
                .stream().sorted((a, b) -> b.getId().compareTo(a.getId())).collect(Collectors.toList());
        return Result.ok(toCards(groups, userId));
    }

    // ==================== 开团 ====================

    @Override
    @Transactional
    public Result createGroup(Long goodsId) {
        Long userId = UserHolder.getUserId();
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null || goods.getStatus() == null || goods.getStatus() != 1) {
            return Result.fail("商品不存在或已不在售");
        }
        if (userId.equals(goods.getSellerId())) {
            return Result.fail("不能对自己发起的商品开团");
        }
        // 同一商品同时只允许一个进行中的拼单（先懒惰过期再查）
        expireOverdue();
        long ongoing = query().eq("goods_id", goodsId).eq("status", GroupBuy.STATUS_ONGOING).count();
        if (ongoing > 0) {
            return Result.fail("该商品已有进行中的拼单，去参团吧");
        }

        LocalDateTime now = LocalDateTime.now();
        GroupBuy group = new GroupBuy()
                .setGoodsId(goodsId)
                .setLeaderUserId(userId)
                .setGroupPrice(calcGroupPrice(goods.getPrice()))
                .setRequiredNum(REQUIRED_NUM)
                .setStatus(GroupBuy.STATUS_ONGOING)
                .setCreateTime(now)
                .setExpireTime(now.plusHours(VALID_HOURS));
        save(group);
        // 团长即第 1 个成员
        GroupMember leader = new GroupMember().setGroupId(group.getId()).setUserId(userId).setCreateTime(now);
        try {
            groupMemberMapper.insert(leader);
        } catch (DuplicateKeyException e) {
            // 理论不可达（拼单刚建），防御唯一键冲突导致开团半途而废
            throw new IllegalStateException("开团写成员失败", e);
        }
        log.info("[GroupBuy] 用户{} 对商品{} 开团成功，拼单价{}，拼单id={}", userId, goodsId, group.getGroupPrice(), group.getId());
        return Result.ok(group.getId());
    }

    // ==================== 参团 ====================

    @Override
    public Result joinGroup(Long groupId) {
        Long userId = UserHolder.getUserId();
        RLock lock = redissonClient.getLock(JOIN_LOCK_KEY + groupId);
        boolean locked = false;
        try {
            // 等 5s 拿锁（极端并发下的排队上限），持有 10s 自动释放兜底
            locked = lock.tryLock(5, 10, java.util.concurrent.TimeUnit.SECONDS);
            if (!locked) {
                return Result.fail("参团人数太多啦，稍后再试");
            }
            return doJoin(groupId, userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("参团失败，请重试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 锁内的参团逻辑（无事务：各语句自动提交，锁内改动立即可见） */
    private Result doJoin(Long groupId, Long userId) {
        GroupBuy group = getById(groupId);
        if (group == null) {
            return Result.fail("拼单不存在");
        }
        // 懒惰过期：到点未成团直接置 3，后续判断自然拒绝
        if (group.getStatus() == GroupBuy.STATUS_ONGOING && group.getExpireTime().isBefore(LocalDateTime.now())) {
            update().eq("id", groupId).eq("status", GroupBuy.STATUS_ONGOING)
                    .set("status", GroupBuy.STATUS_EXPIRED).update();
            group.setStatus(GroupBuy.STATUS_EXPIRED);
        }
        if (group.getStatus() == GroupBuy.STATUS_EXPIRED) {
            return Result.fail("拼单已过期，可以去商品页重新开团");
        }
        if (group.getStatus() != GroupBuy.STATUS_ONGOING) {
            return Result.fail("这个拼单已经成团啦");
        }
        if (userId.equals(group.getLeaderUserId())) {
            return Result.fail("不能参加自己发起的拼单");
        }
        // 商品必须仍在售：已售出/下架的团就地作废，避免"商品没了还能拼"
        Goods goods = goodsMapper.selectById(group.getGoodsId());
        if (goods == null || goods.getStatus() == null || goods.getStatus() != 1) {
            update().eq("id", groupId).eq("status", GroupBuy.STATUS_ONGOING)
                    .set("status", GroupBuy.STATUS_EXPIRED).update();
            return Result.fail("该商品已不在售，拼单已自动作废");
        }

        long joined = groupMemberMapper.selectCount(
                new QueryWrapper<GroupMember>().eq("group_id", groupId));
        if (joined >= group.getRequiredNum()) {
            return Result.fail("手慢一步，拼单已满员");
        }
        // 已参加过 → 幂等成功（网络重试不报错）
        if (groupMemberMapper.selectCount(new QueryWrapper<GroupMember>()
                .eq("group_id", groupId).eq("user_id", userId)) > 0) {
            return Result.ok(group.getId());
        }

        try {
            groupMemberMapper.insert(new GroupMember().setGroupId(groupId).setUserId(userId)
                    .setCreateTime(LocalDateTime.now()));
        } catch (DuplicateKeyException e) {
            // 并发重复参团被唯一键拦下：按幂等成功处理
            return Result.ok(group.getId());
        }

        long after = groupMemberMapper.selectCount(new QueryWrapper<GroupMember>().eq("group_id", groupId));
        if (after >= group.getRequiredNum()) {
            update().set("status", GroupBuy.STATUS_SUCCESS).eq("id", groupId).update();
            log.info("[GroupBuy] 拼单{} 成团（{}人）", groupId, after);
            return Result.ok(groupId);
        }
        return Result.ok(groupId);
    }

    // ==================== 内部工具 ====================

    /** 拼单价：9 折，向上保留整数元（二手价位数不大，直接取整更直观） */
    private Long calcGroupPrice(Long price) {
        if (price == null || price <= 0) {
            return 0L;
        }
        return (long) Math.ceil(price * GROUP_DISCOUNT);
    }

    /** 批量懒惰过期：到点未成团 → 3 */
    private void expireOverdue() {
        update().eq("status", GroupBuy.STATUS_ONGOING)
                .lt("expire_time", LocalDateTime.now())
                .set("status", GroupBuy.STATUS_EXPIRED).update();
    }

    /**
     * 组装拼单卡片：一次 batch 查商品（避免逐团查商品的 N+1）
     * @param groups    拼单列表
     * @param perspective 视角用户（"我的拼单"传当前用户以标记角色，大厅/详情传 null）
     */
    private List<GroupCardDTO> toCards(List<GroupBuy> groups, Long perspective) {
        if (groups.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> goodsIds = groups.stream().map(GroupBuy::getGoodsId).distinct().collect(Collectors.toList());
        Map<Long, Goods> goodsMap = goodsMapper.selectBatchIds(goodsIds).stream()
                .collect(Collectors.toMap(Goods::getId, Function.identity()));

        List<GroupCardDTO> cards = new ArrayList<>(groups.size());
        for (GroupBuy g : groups) {
            GroupCardDTO c = new GroupCardDTO();
            c.setId(g.getId());
            c.setGoodsId(g.getGoodsId());
            c.setGroupPrice(g.getGroupPrice());
            c.setRequiredNum(g.getRequiredNum());
            c.setStatus(g.getStatus());
            c.setLeaderId(g.getLeaderUserId());
            c.setExpireMs(g.getExpireTime() == null ? null
                    : g.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            c.setJoinedNum((int) groupMemberMapper.selectCount(
                    new QueryWrapper<GroupMember>().eq("group_id", g.getId())));
            Goods goods = goodsMap.get(g.getGoodsId());
            if (goods != null) {
                c.setGoodsName(goods.getName());
                c.setGoodsImg((goods.getImages() == null ? "" : goods.getImages()).split(",")[0]);
                c.setPrice(goods.getPrice());
            }
            if (perspective != null) {
                // 角色必须以成员表为准：团长=leader；查到成员记录才算 member；
                // 否则保持 null（任何登录用户看别人的团都不能被误标成"我参加的"，
                // 否则前端会隐藏参团按钮，登录用户将永远无法参团）
                boolean isLeader = perspective.equals(g.getLeaderUserId());
                boolean isMember = !isLeader && groupMemberMapper.selectCount(
                        new QueryWrapper<GroupMember>()
                                .eq("group_id", g.getId())
                                .eq("user_id", perspective)) > 0;
                c.setMyRole(isLeader ? "leader" : (isMember ? "member" : null));
            }
            cards.add(c);
        }
        return cards;
    }
}
