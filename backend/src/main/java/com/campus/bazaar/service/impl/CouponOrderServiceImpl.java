package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.CouponOrder;
import com.campus.bazaar.entity.SeckillCoupon;
import com.campus.bazaar.mapper.CouponOrderMapper;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CouponOrderServiceImpl extends ServiceImpl<CouponOrderMapper, CouponOrder> implements ICouponOrderService {

    @Resource
    private SeckillCouponMapper seckillCouponMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
            SeckillMessage message = new SeckillMessage(couponId, userId);
            try {
                rabbitTemplate.convertAndSend(MqConstants.SECKILL_EXCHANGE, MqConstants.SECKILL_ROUTING_KEY, message);
            } catch (Exception e) {
                // 发消息失败：回滚 Redis 预扣（库存 +1、移除用户记录），避免"库存扣了订单没建"
                log.error("[Seckill] MQ 发送失败，回滚预扣: couponId={}, userId={}, err={}", couponId, userId, e.getMessage());
                compensate(couponId, userId);
                return Result.fail("系统繁忙，请稍后再试");
            }
            log.info("[Seckill] 预扣成功，异步下单受理: couponId={}, userId={}", couponId, userId);
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

        // 1. DB 层一人一单二次校验（Redis 可能被清，DB 是最终真相）
        Integer count = query().eq("user_id", userId).eq("coupon_id", couponId).count();
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

        // 3. 创建订单
        CouponOrder order = new CouponOrder();
        order.setId(System.currentTimeMillis());
        order.setUserId(userId);
        order.setCouponId(couponId);
        order.setStatus(1); // 未支付
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
}
