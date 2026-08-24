package com.campus.bazaar.utils;

import java.lang.annotation.*;

/**
 * 接口级限流注解（Redis 分布式令牌桶）
 * <p>
 * 标注在 Controller 方法或类上，由 {@link com.campus.bazaar.config.RateLimitInterceptor} 解析执行。
 * 用法示例：
 * <pre>
 * @RateLimit(key = "seckill", rate = 5, capacity = 10)   // 每秒补充 5 个令牌，桶容量 10
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流维度 key（会拼成 Redis key：rate:{key}:{userId|ip}） */
    String key() default "default";

    /** 令牌补充速率：每秒补充多少个令牌 */
    double rate() default 10;

    /** 令牌桶容量：允许的最大突发量 */
    int capacity() default 20;

    /** 超限时的提示语 */
    String message() default "请求过于频繁，请稍后再试";
}
