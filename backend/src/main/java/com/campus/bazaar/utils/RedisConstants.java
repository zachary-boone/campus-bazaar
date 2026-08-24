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
    public static final String POST_LIKED_KEY = "post:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String GOODS_GEO_KEY = "goods:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
