package com.campus.bazaar.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_GOODS_KEY = "cache:goods:";

    public static final String LOCK_GOODS_KEY = "lock:goods:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_USER_KEY = "seckill:user:";

    /** 商品秒杀（由券秒杀迁移而来，key 与券秒杀的互不干扰） */
    /** 商品预扣库存：seckill:goods:stock:{goodsId} */
    public static final String SECKILL_GOODS_STOCK_KEY = "seckill:goods:stock:";
    /** 商品秒杀已抢用户集合：seckill:goods:user:{goodsId}（一人一单） */
    public static final String SECKILL_GOODS_USER_KEY = "seckill:goods:user:";
    public static final String POST_LIKED_KEY = "post:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String GOODS_GEO_KEY = "goods:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    /** 验证码发送频率限制（ZSet 滑动窗口，member=时间戳） */
    public static final String CODE_LIMIT_MIN_KEY = "login:code:limit:min:";
    public static final String CODE_LIMIT_HOUR_KEY = "login:code:limit:hour:";
    public static final long CODE_LIMIT_MIN_TTL = 60L;       // 一级窗口：60 秒
    public static final long CODE_LIMIT_HOUR_TTL = 3600L;    // 二级窗口：1 小时
    public static final int CODE_LIMIT_MIN_COUNT = 3;        // 60s 内最多 3 次
    public static final int CODE_LIMIT_HOUR_COUNT = 10;      // 1h 内最多 10 次

    /** 支付状态轮询（RabbitMQ 延迟队列） */
    public static final String PAY_RETRY_KEY = "pay:retry:"; // 订单支付轮询重试级别（备用落点）
}
