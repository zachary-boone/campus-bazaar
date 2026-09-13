package com.campus.bazaar.mq;

import com.campus.bazaar.service.IOrderService;
import com.campus.bazaar.utils.MqConstants;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 商品秒杀异步下单消费者（手动 ack）
 * <p>
 * 与券秒杀的 {@link SeckillOrderConsumer} 结构一致，只是消费的是商品秒杀消息、
 * 落库对象为 tb_order 订单表。
 * <p>
 * 用 <b>Redisson 分布式锁（锁用户维度）</b> 包住事务建单：
 * - 锁在事务外获取，保证"检查一人一单 + 扣库存 + 建单"整体串行，
 *   解决 @Transactional 内部 check-then-act 在并发下的事务锁失效问题；
 * - 不同用户并行消费，同一用户串行，吞吐不受影响。
 * <p>
 * 可靠性：处理成功 basicAck；业务失败 basicNack(requeue=true) 重新入队重试。
 */
@Slf4j
@Component
public class GoodsSeckillConsumer {

    @Resource
    private IOrderService orderService;

    @Resource
    private RedissonClient redissonClient;

    @RabbitListener(queues = MqConstants.GOODS_SECKILL_QUEUE)
    public void createOrder(GoodsSeckillMessage message, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        RLock lock = redissonClient.getLock("lock:goods:order:" + message.getUserId());
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("[GoodsSeckill] 获取用户锁失败，消息重试: userId={}", message.getUserId());
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            orderService.createGoodsSeckillOrder(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[GoodsSeckill] 消费失败，重新入队: msgId={}, err={}", message.getMsgId(), e.getMessage());
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ignore) {
            }
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
