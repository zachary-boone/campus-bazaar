package com.campus.bazaar.utils;

import java.lang.annotation.*;

/**
 * 接口防重复提交注解
 * <p>
 * 标注在需要防抖的接口上（如下单、支付），
 * 由 {@link com.campus.bazaar.config.IdempotentInterceptor} 用 Redis SETNX 实现：
 * 同一用户同一接口在 ttl 秒内重复请求直接拒绝。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /** 防抖窗口（秒），默认 3 秒 */
    int ttl() default 3;

    /** 超限提示语 */
    String message() default "操作过于频繁，请勿重复提交";
}
