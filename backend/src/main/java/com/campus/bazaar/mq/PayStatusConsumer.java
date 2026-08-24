package com.campus.bazaar.mq;

import com.campus.bazaar.entity.Order;
import com.campus.bazaar.service.IOrderService;
import com.campus.bazaar.utils.MqConstants;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 支付状态轮询消费者（RabbitMQ 延迟队列 + 指数退避，手动 ack）
 * <p>
 * 下单后投递第 1 级延迟消息（5s），到期转投到本队列；本消费者检查订单支付状态：
 * - 已支付/已完成/已取消 → 结束（说明用户在检查间隙完成了支付/取消，无需处理）；
 * - 未支付且未达最大重试级别 → 投递到下一级延迟队列（TTL 翻倍：5s→10s→20s→40s→80s）；
 * - 未支付且达到最大级别（80s 后仍没付）→ 关单，回收订单。
 * <p>
 * 相比前端定时轮询：查询动作由消息驱动、错峰进行，接口无需在响应里等待支付结果，
 * 也避免了大量无效的重复请求（指数退避把检查次数压到 ~5 次）。
 */
@Slf4j
@Component
public class PayStatusConsumer {

    @Resource
    private IOrderService orderService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = MqConstants.PAY_CHECK_QUEUE)
    public void checkPayStatus(PayCheckMessage message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            String orderNo = message.getOrderNo();
            Order order = orderService.queryOrderInternal(orderNo);
            if (order == null) {
                log.warn("[PayCheck] 订单不存在，结束轮询: {}", orderNo);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 已支付(2)/已完成(3)/已取消(4)：流程结束
            if (order.getStatus() != null && order.getStatus() != 1) {
                log.info("[PayCheck] 订单已处理(status={})，结束轮询: {}", order.getStatus(), orderNo);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 未支付：指数退避升级重试
            if (message.getRetryLevel() < MqConstants.PAY_MAX_RETRY) {
                int nextLevel = message.getRetryLevel() + 1;
                rabbitTemplate.convertAndSend(
                        MqConstants.PAY_DELAY_EXCHANGE,
                        MqConstants.PAY_DELAY_ROUTING_PREFIX + nextLevel,
                        new PayCheckMessage(orderNo, nextLevel));
                log.info("[PayCheck] 未支付，{}ms 后重试(level={}): {}",
                        MqConstants.PAY_DELAY_MS[nextLevel - 1], nextLevel, orderNo);
            } else {
                // 达到最大级别：超时关单
                orderService.timeoutCancelOrder(orderNo);
                log.warn("[PayCheck] 支付超时，订单已关闭: {}", orderNo);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[PayCheck] 消费失败，重新入队: orderNo={}, err={}", message.getOrderNo(), e.getMessage());
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ignore) {
            }
        }
    }
}
