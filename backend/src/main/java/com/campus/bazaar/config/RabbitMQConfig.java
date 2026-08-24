package com.campus.bazaar.config;

import com.campus.bazaar.utils.MqConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列/交换机声明
 * <p>
 * 秒杀异步下单：
 *   seckill.exchange(direct) ──seckill.order──▶ seckill.order.queue
 * <p>
 * 支付状态轮询（延迟队列方案：TTL + 死信交换机）：
 *   投递 pay.delay.exchange(topic)，按级别落到对应 TTL 队列，
 *   TTL 到期消息经 x-dead-letter-exchange 转投 pay.check.queue，
 *   消费者检查支付状态，未支付则升级级别重新投递（指数退避），
 *   达到最大级别仍未支付则关单。
 *   pay.delay.1.queue (TTL 5s)  ─┐
 *   pay.delay.2.queue (TTL 10s) ─┼─(死信)─▶ pay.check.exchange ──▶ pay.check.queue
 *   ...                         ─┘
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 统一 JSON 序列化（生产者 RabbitTemplate 与消费者 Listener 容器共用）。
     * 消息体为 JSON + 类型头，便于跨语言消费和排障。
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** ========== 秒杀异步下单 ========== */
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(MqConstants.SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(MqConstants.SECKILL_QUEUE).build();
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange()).with(MqConstants.SECKILL_ROUTING_KEY);
    }

    /** ========== 支付状态轮询 ========== */
    @Bean
    public TopicExchange payDelayExchange() {
        return new TopicExchange(MqConstants.PAY_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange payCheckExchange() {
        return new DirectExchange(MqConstants.PAY_CHECK_EXCHANGE, true, false);
    }

    @Bean
    public Queue payCheckQueue() {
        return QueueBuilder.durable(MqConstants.PAY_CHECK_QUEUE).build();
    }

    @Bean
    public Binding payCheckBinding() {
        return BindingBuilder.bind(payCheckQueue())
                .to(payCheckExchange()).with(MqConstants.PAY_CHECK_ROUTING_KEY);
    }

    /** 第 1 级延迟队列：5s */
    @Bean
    public Queue payDelayQueue1() {
        return delayQueue(MqConstants.PAY_DELAY_ROUTING_PREFIX + 1, MqConstants.PAY_DELAY_MS[0]);
    }

    /** 第 2 级延迟队列：10s */
    @Bean
    public Queue payDelayQueue2() {
        return delayQueue(MqConstants.PAY_DELAY_ROUTING_PREFIX + 2, MqConstants.PAY_DELAY_MS[1]);
    }

    /** 第 3 级延迟队列：20s */
    @Bean
    public Queue payDelayQueue3() {
        return delayQueue(MqConstants.PAY_DELAY_ROUTING_PREFIX + 3, MqConstants.PAY_DELAY_MS[2]);
    }

    /** 第 4 级延迟队列：40s */
    @Bean
    public Queue payDelayQueue4() {
        return delayQueue(MqConstants.PAY_DELAY_ROUTING_PREFIX + 4, MqConstants.PAY_DELAY_MS[3]);
    }

    /** 第 5 级延迟队列：80s */
    @Bean
    public Queue payDelayQueue5() {
        return delayQueue(MqConstants.PAY_DELAY_ROUTING_PREFIX + 5, MqConstants.PAY_DELAY_MS[4]);
    }

    @Bean
    public Binding payDelayBinding1() {
        return delayBinding(payDelayQueue1(), MqConstants.PAY_DELAY_ROUTING_PREFIX + 1);
    }

    @Bean
    public Binding payDelayBinding2() {
        return delayBinding(payDelayQueue2(), MqConstants.PAY_DELAY_ROUTING_PREFIX + 2);
    }

    @Bean
    public Binding payDelayBinding3() {
        return delayBinding(payDelayQueue3(), MqConstants.PAY_DELAY_ROUTING_PREFIX + 3);
    }

    @Bean
    public Binding payDelayBinding4() {
        return delayBinding(payDelayQueue4(), MqConstants.PAY_DELAY_ROUTING_PREFIX + 4);
    }

    @Bean
    public Binding payDelayBinding5() {
        return delayBinding(payDelayQueue5(), MqConstants.PAY_DELAY_ROUTING_PREFIX + 5);
    }

    /** 构建延迟队列：TTL 到期 → 死信到支付检查交换机 */
    private Queue delayQueue(String name, long ttlMs) {
        return QueueBuilder.durable(name)
                .withArgument("x-message-ttl", ttlMs)
                .withArgument("x-dead-letter-exchange", MqConstants.PAY_CHECK_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MqConstants.PAY_CHECK_ROUTING_KEY)
                .build();
    }

    private Binding delayBinding(Queue queue, String routingKey) {
        return BindingBuilder.bind(queue)
                .to(payDelayExchange()).with(routingKey);
    }
}
