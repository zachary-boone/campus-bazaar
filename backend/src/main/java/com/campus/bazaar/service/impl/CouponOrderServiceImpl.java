package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.CouponOrder;
import com.campus.bazaar.entity.MqIdempotent;
import com.campus.bazaar.entity.SeckillCoupon;
import com.campus.bazaar.mapper.CouponOrderMapper;
import com.campus.bazaar.mapper.MqIdempotentMapper;
import com.campus.bazaar.mapper.SeckillCouponMapper;
import com.campus.bazaar.mq.SeckillMessage;
import com.campus.bazaar.service.ICouponOrderService;
import com.campus.bazaar.utils.MqConstants;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CouponOrderServiceImpl extends ServiceImpl<CouponOrderMapper, CouponOrder> implements ICouponOrderService {

    @Resource
    private SeckillCouponMapper seckillCouponMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MqIdempotentMapper mqIdempotentMapper;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 秒杀 Lua 脚本（原子完成"查库存 + 查重复 + 预扣库存 + 记用户"）：
     * KEYS[1] = seckill:stock:{couponId}
     * KEYS[2] = seckill:user:{couponId}
     * ARGV[1] = userId
     * 返回：1=成功；-1=库存不足；-2=每人限购一张
     */
    private static final String SECKILL_LUA =
            "if tonumber(redis.call('get', KEYS[1]) or '0') <= 0 then return -1 end " +
            "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then return -2 end " +
            "redis.call('incrby', KEYS[1], -1) " +
            "redis.call('sadd', KEYS[2], ARGV[1]) " +
            "return 1";

    @Override
    public Result seckillCoupon(Long couponId) {
        Long userId = UserHolder.getUserId();
        if (com.campus.bazaar.metrics.BizMetrics.seckillRequests != null) {
            com.campus.bazaar.metrics.BizMetrics.seckillRequests.increment();
        }

        // 1. 校验秒杀时间
        SeckillCoupon seckillCoupon = seckillCouponMapper.selectById(couponId);
        if (seckillCoupon == null) {
            return Result.fail("优惠券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(seckillCoupon.getBeginTime())) {
            return Result.fail("秒杀尚未开始");
        }
        if (now.isAfter(seckillCoupon.getEndTime())) {
            return Result.fail("秒杀已经结束");
        }

        // 2. Redis 库存预热（首次访问时从 DB 拉取，之后以 Redis 为准做预扣）
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisConstants.SECKILL_STOCK_KEY + couponId, String.valueOf(seckillCoupon.getStock()));

        // 3. Redisson 分布式锁：同一优惠券的秒杀串行化，
        //    解决"检查库存 → 预扣 → 发消息"之间的竞态（事务锁失效缺陷的经典场景）
        RLock lock = redissonClient.getLock("lock:coupon:seckill:" + couponId);
        try {
            if (!lock.tryLock(0, 5, TimeUnit.SECONDS)) {
                return Result.fail("系统繁忙，请稍后再试");
            }
            // 4. Lua 原子预扣库存 + 一人一单校验
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SECKILL_LUA, Long.class);
            Long result = stringRedisTemplate.execute(script, Arrays.asList(
                            RedisConstants.SECKILL_STOCK_KEY + couponId,
                            RedisConstants.SECKILL_USER_KEY + couponId),
                    userId.toString());

            if (result == null || result == -1L) {
                return Result.fail("库存不足");
            }
            if (result == -2L) {
                return Result.fail("每人限购一张");
            }

            // 5. 发送 MQ 异步下单，立即返回（削峰：数据库只承受"实际下单量"的写压力）
            SeckillMessage message = new SeckillMessage(couponId, userId, UUID.randomUUID().toString().replace("-", ""));
            try {
                rabbitTemplate.convertAndSend(MqConstants.SECKILL_EXCHANGE, MqConstants.SECKILL_ROUTING_KEY, message);
            } catch (Exception e) {
                // 发消息失败：回滚 Redis 预扣（库存 +1、移除用户记录），避免"库存扣了订单没建"
                log.error("[Seckill] MQ 发送失败，回滚预扣: couponId={}, userId={}, err={}", couponId, userId, e.getMessage());
                compensate(couponId, userId);
                return Result.fail("系统繁忙，请稍后再试");
            }
            log.info("[Seckill] 预扣成功，异步下单受理: couponId={}, userId={}", couponId, userId);
            if (com.campus.bazaar.metrics.BizMetrics.seckillSuccess != null) {
                com.campus.bazaar.metrics.BizMetrics.seckillSuccess.increment();
            }
            return Result.ok(0L); // 0 表示已受理，订单由消费者异步创建
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("系统繁忙，请稍后再试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * MQ 消费者调用：事务内二次校验（DB 一人一单）+ DB 乐观扣减库存 + 创建订单。
     * 由消费者用 Redisson 锁（锁用户维度）包住本方法，解决事务锁失效。
     */
    @Override
    @Transactional
    public void createSeckillOrder(SeckillMessage message) {
        Long couponId = message.getCouponId();
        Long userId = message.getUserId();

        // 0. 消息幂等：唯一键抢占，已处理过的消息直接跳过（防止 MQ 重复投递导致重复下单）
        int idem = mqIdempotentMapper.insertIgnore(message.getMsgId(), "seckill_order");
        if (idem == 0) {
            log.info("[Seckill] 重复消息，幂等跳过: msgId={}", message.getMsgId());
            return;
        }

        // 1. DB 层一人一单二次校验（Redis 可能被清，DB 是最终真相；超时关闭 -1 的单不占名额，可重新抢）
        Integer count = query().eq("user_id", userId).eq("coupon_id", couponId)
                .ne("status", -1).count();
        if (count > 0) {
            log.warn("[Seckill] 重复下单，补偿库存: couponId={}, userId={}", couponId, userId);
            compensate(couponId, userId);
            return;
        }

        // 2. DB 乐观扣减库存（CAS：库存>0 才扣）
        int update = seckillCouponMapper.update(null, new UpdateWrapper<SeckillCoupon>()
                .eq("coupon_id", couponId)
                .gt("stock", 0)
                .setSql("stock = stock - 1"));
        if (update == 0) {
            log.warn("[Seckill] DB 库存不足，补偿 Redis: couponId={}, userId={}", couponId, userId);
            compensate(couponId, userId);
            return;
        }

        // 3. 创建订单（初始状态 0=未支付，与表定义一致）
        CouponOrder order = new CouponOrder();
        order.setId(System.currentTimeMillis());
        order.setUserId(userId);
        order.setCouponId(couponId);
        order.setStatus(0); // 未支付
        order.setCreateTime(LocalDateTime.now());
        save(order);

        log.info("[Seckill] 异步下单成功: orderId={}, couponId={}, userId={}", order.getId(), couponId, userId);
    }

    /**
     * 补偿：下单失败时回滚 Redis 预扣的库存与用户记录
     */
    private void compensate(Long couponId, Long userId) {
        stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + couponId);
        stringRedisTemplate.opsForSet().remove(RedisConstants.SECKILL_USER_KEY + couponId, userId.toString());
    }

    @Override
    @Transactional
    public Result payCouponOrder(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        CouponOrder order = getById(id);
        if (order == null) {
            return Result.fail("券订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("只能支付自己的券订单");
        }
        if (order.getStatus() != 0) {
            return Result.fail("当前状态无法支付");
        }
        // 原子流转 0→1，防止重复支付
        boolean updated = update(new UpdateWrapper<CouponOrder>()
                .eq("id", id).eq("status", 0)
                .set("status", 1).set("pay_type", 1).set("pay_time", LocalDateTime.now()));
        if (!updated) {
            return Result.fail("券订单状态异常，请刷新后重试");
        }
        log.info("[CouponOrder] 支付成功: id={}, userId={}", id, userId);
        return Result.ok("支付成功");
    }

    @Override
    @Transactional
    public Result verifyCouponOrder(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        CouponOrder order = getById(id);
        if (order == null) {
            return Result.fail("券订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("只能核销自己的券订单");
        }
        if (order.getStatus() != 1) {
            return Result.fail("券未支付或已被使用");
        }
        boolean updated = update(new UpdateWrapper<CouponOrder>()
                .eq("id", id).eq("status", 1)
                .set("status", 2).set("use_time", LocalDateTime.now()));
        if (!updated) {
            return Result.fail("券订单状态异常，请刷新后重试");
        }
        log.info("[CouponOrder] 核销成功: id={}, userId={}", id, userId);
        return Result.ok("核销成功");
    }

    @Override
    @Transactional
    public int cancelExpiredCouponOrders(int minutes) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(minutes);
        // 找出超时未支付(0)的券订单
        List<CouponOrder> expired = query()
                .eq("status", 0)
                .lt("create_time", deadline)
                .list();
        if (expired.isEmpty()) {
            return 0;
        }
        int cancelled = 0;
        for (CouponOrder order : expired) {
            // 原子关闭 0→-1（防并发重复关闭）
            boolean updated = update(new UpdateWrapper<CouponOrder>()
                    .eq("id", order.getId()).eq("status", 0)
                    .set("status", -1));
            if (updated) {
                // 回补库存：DB +1、Redis 预扣 +1、解除用户秒杀记录（允许重新抢）
                seckillCouponMapper.update(null, new UpdateWrapper<SeckillCoupon>()
                        .eq("coupon_id", order.getCouponId())
                        .setSql("stock = stock + 1"));
                compensate(order.getCouponId(), order.getUserId());
                cancelled++;
                log.info("[CouponOrder] 超时关单并回补库存: id={}, couponId={}, userId={}",
                        order.getId(), order.getCouponId(), order.getUserId());
            }
        }
        return cancelled;
    }
}
