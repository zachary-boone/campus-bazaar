package com.campus.bazaar.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * Redis 分布式令牌桶限流器
 * <p>
 * 用一段 Lua 脚本实现原子令牌桶：
 * - 记录桶内剩余令牌 tokens 与上次补充时间 ts 两个 key；
 * - 每次请求按 (now - last) * rate 补充令牌（上限 capacity）；
 * - 有令牌则取 1 放行，无令牌拒绝。
 * 整个判断 + 扣减在 Redis 单线程内原子完成，多实例部署同样准确。
 */
@Component
public class RedisTokenBucket {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 令牌桶 Lua：KEYS[1]=限流key  ARGV[1]=rate  ARGV[2]=capacity  ARGV[3]=now；返回 1=放行 0=拒绝 */
    private static final String LUA_SCRIPT =
            "local key_tokens = KEYS[1] .. ':tokens' " +
            "local key_ts = KEYS[1] .. ':ts' " +
            "local rate = tonumber(ARGV[1]) " +
            "local capacity = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +
            "local last_tokens = tonumber(redis.call('get', key_tokens) or '0') " +
            "local last_ts = tonumber(redis.call('get', key_ts) or '0') " +
            "if last_tokens == 0 and last_ts == 0 then last_tokens = capacity end " +
            "local delta = math.max(0, now - last_ts) " +
            "local filled = math.min(capacity, last_tokens + delta * rate) " +
            "if filled >= 1 then " +
            "  redis.call('set', key_tokens, filled - 1) " +
            "  redis.call('set', key_ts, now) " +
            "  return 1 " +
            "else " +
            "  redis.call('set', key_tokens, filled) " +
            "  redis.call('set', key_ts, now) " +
            "  return 0 " +
            "end";

    /**
     * 尝试获取一个令牌
     * @param key     限流维度 key
     * @param rate    每秒补充速率
     * @param capacity 桶容量
     * @return true=放行 false=拒绝
     */
    public boolean tryAcquire(String key, double rate, int capacity) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(rate),
                String.valueOf(capacity),
                String.valueOf(System.currentTimeMillis() / 1000)
        );
        return result != null && result == 1L;
    }
}
