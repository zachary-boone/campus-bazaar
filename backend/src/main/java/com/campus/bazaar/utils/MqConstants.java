package com.campus.bazaar.utils;

/**
 * RabbitMQ 常量
 * <p>
 * 两套队列体系：
 * 1. 秒杀异步下单：seckill.exchange → seckill.order.queue
 * 2. 支付状态轮询：5 级 TTL 延迟队列（pay.delay.1~5）→ 死信 → pay.check.queue
 *    延迟时间按指数退避：5s / 10s / 20s / 40s / 80s
 */
public class MqConstants {

    /** ========== 秒杀异步下单 ========== */
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    /** ========== 支付状态轮询（延迟队列 + 死信） ========== */
    /** 延迟交换机（topic）：接收 pay.delay.1 ~ pay.delay.5 */
    public static final String PAY_DELAY_EXCHANGE = "pay.delay.exchange";
    /** 死信目标交换机（direct） */
    public static final String PAY_CHECK_EXCHANGE = "pay.check.exchange";
    /** 支付检查队列 */
    public static final String PAY_CHECK_QUEUE = "pay.check.queue";
    public static final String PAY_CHECK_ROUTING_KEY = "pay.check";
    /** 延迟队列 routingKey 前缀：pay.delay.{level} */
    public static final String PAY_DELAY_ROUTING_PREFIX = "pay.delay.";
    /** 指数退避延迟时间（毫秒）：第 N 级延迟 = PAY_DELAY_MS[N-1] */
    public static final long[] PAY_DELAY_MS = {5000L, 10000L, 20000L, 40000L, 80000L};
    /** 最大重试级别（超过则关单） */
    public static final int PAY_MAX_RETRY = 5;
}
