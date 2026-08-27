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
 * 用 Redis 分布式令牌桶做双层限流保护：
 * <ul>
 *   <li>全局维度（rate:{key}:global）：配置 globalRate/globalCapacity 时启用，
 *       挡住全体用户同时涌入的流量洪峰（接口级兜底）；</li>
 *   <li>单用户维度（已登录 rate:{key}:u{userId}，匿名 rate:{key}:ip{ip}）：
 *       防止单个用户用连点器/脚本刷接口。</li>
 * </ul>
 * 任一维度超限返回 HTTP 429 + JSON 提示。
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

        // 第一层：全局维度兜底桶（防全体用户流量洪峰，不区分用户）
        if (rateLimit.globalRate() > 0 && rateLimit.globalCapacity() > 0) {
            String globalKey = "rate:" + rateLimit.key() + ":global";
            boolean globalPass = redisTokenBucket.tryAcquire(globalKey, rateLimit.globalRate(), rateLimit.globalCapacity());
            if (!globalPass) {
                log.warn("[RateLimit] {} 全局维度限流（rate={}, capacity={}）", globalKey, rateLimit.globalRate(), rateLimit.globalCapacity());
                if (com.campus.bazaar.metrics.BizMetrics.rateLimitRejected != null) {
                    com.campus.bazaar.metrics.BizMetrics.rateLimitRejected.increment();
                }
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"errorMsg\":\"" + rateLimit.message() + "\"}");
                return false;
            }
        }

        // 第二层：单用户维度桶（防连点器/脚本）：用户维度优先，匿名用 IP
        String dimension;
        if (UserHolder.getUserId() != null) {
            dimension = "u" + UserHolder.getUserId();
        } else {
            dimension = "ip" + getIp(request);
        }
        String key = "rate:" + rateLimit.key() + ":" + dimension;

        boolean pass = redisTokenBucket.tryAcquire(key, rateLimit.rate(), rateLimit.capacity());
        if (!pass) {
            log.warn("[RateLimit] {} 单用户维度限流（rate={}, capacity={}）", key, rateLimit.rate(), rateLimit.capacity());
            if (com.campus.bazaar.metrics.BizMetrics.rateLimitRejected != null) {
                com.campus.bazaar.metrics.BizMetrics.rateLimitRejected.increment();
            }
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
