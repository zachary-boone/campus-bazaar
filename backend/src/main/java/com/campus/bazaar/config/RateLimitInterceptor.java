package com.campus.bazaar.config;

import cn.hutool.core.util.StrUtil;
import com.campus.bazaar.utils.RateLimit;
import com.campus.bazaar.utils.RedisTokenBucket;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 接口限流拦截器（order=0，最高优先级）
 * <p>
 * 扫描 Controller 方法上的 {@link RateLimit} 注解，
 * 用 Redis 分布式令牌桶对关键接口做接口级限流保护：
 * - 已登录：key 维度 = 用户（rate:{key}:u{userId}）
 * - 未登录：key 维度 = IP（rate:{key}:ip{ip}）
 * 超限返回 HTTP 429 + JSON 提示。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Resource
    private RedisTokenBucket redisTokenBucket;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // 方法上优先，其次类上
        RateLimit rateLimit = ((HandlerMethod) handler).getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            rateLimit = ((HandlerMethod) handler).getBeanType().getAnnotation(RateLimit.class);
        }
        if (rateLimit == null) {
            return true;
        }

        // 构造限流维度 key：用户维度优先，匿名用 IP
        String dimension;
        if (UserHolder.getUserId() != null) {
            dimension = "u" + UserHolder.getUserId();
        } else {
            dimension = "ip" + getIp(request);
        }
        String key = "rate:" + rateLimit.key() + ":" + dimension;

        boolean pass = redisTokenBucket.tryAcquire(key, rateLimit.rate(), rateLimit.capacity());
        if (!pass) {
            log.warn("[RateLimit] {} 触发限流（rate={}, capacity={}）", key, rateLimit.rate(), rateLimit.capacity());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"errorMsg\":\"" + rateLimit.message() + "\"}");
            return false;
        }
        return true;
    }

    private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
