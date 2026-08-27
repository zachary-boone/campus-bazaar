package com.campus.bazaar.utils;

import java.lang.annotation.*;

/**
 * 接口级限流注解（Redis 分布式令牌桶，双层维度）
 * <p>
 * 标注在 Controller 方法或类上，由 {@link com.campus.bazaar.config.RateLimitInterceptor} 解析执行。
 * 双层限流：先校验全局维度桶（globalRate/globalCapacity，防全体用户流量洪峰），
 * 再校验单用户维度桶（rate/capacity，防连点器/脚本）。
 * 用法示例：
 * <pre>
 * // 每秒补充 5 个令牌，桶容量 10（单用户维度）
 * @RateLimit(key = "seckill", rate = 5, capacity = 10)
 *
 * // 单用户 5/s + 全局 200/s 双层限流
 * @RateLimit(key = "seckill", rate = 5, capacity = 10, globalRate = 200, globalCapacity = 500)
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流维度 key（会拼成 Redis key：rate:{key}:{userId|ip} 与 rate:{key}:global） */
    String key() default "default";

    /** 令牌补充速率：每秒补充多少个令牌（单用户维度） */
    double rate() default 10;

    /** 令牌桶容量：允许的最大突发量（单用户维度） */
    int capacity() default 20;

    /** 全局令牌补充速率：每秒补充多少个令牌；0 表示不启用全局维度兜底 */
    double globalRate() default 0;

    /** 全局令牌桶容量：允许的最大突发量；globalRate=0 时忽略 */
    int globalCapacity() default 0;

    /** 超限时的提示语 */
    String message() default "请求过于频繁，请稍后再试";
}
