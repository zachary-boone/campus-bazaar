package com.campus.bazaar.mq;

import com.campus.bazaar.service.ICouponOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀异步下单消费者
 * <p>
 * 收到消息后用 <b>Redisson 分布式锁（锁用户维度）</b> 包住事务建单：
 * - 锁在事务外获取，保证"检查一人一单 + 扣库存 + 建单"整体串行，
 *   解决 @Transactional 内部 check-then-act 在并发下的事务锁失效问题；
 * - 不同用户并行消费，同一用户串行，吞吐不受影响。
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    @Resource
    private ICouponOrderService couponOrderService;

    @Resource
    private RedissonClient redissonClient;

    @RabbitListener(queues = "seckill.order.queue")
    public void createOrder(SeckillMessage message) {
        RLock lock = redissonClient.getLock("lock:coupon:order:" + message.getUserId());
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("[Seckill] 获取用户锁失败，消息稍后重试: userId={}", message.getUserId());
                throw new RuntimeException("acquire lock timeout: " + message.getUserId());
            }
            couponOrderService.createSeckillOrder(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
