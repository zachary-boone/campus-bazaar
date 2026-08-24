package com.campus.bazaar.config;

import com.campus.bazaar.utils.Idempotent;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 接口防重复提交拦截器（order=3，登录校验之后）
 * <p>
 * 扫描 {@link Idempotent} 注解，用 Redis SETNX 做防抖：
 * key = idem:{userId}:{uri}，ttl 秒内第二次请求直接拒绝。
 * 典型场景：用户连点"下单/支付"按钮，避免重复创建订单。
 */
@Slf4j
@Component
public class IdempotentInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        Idempotent idem = ((HandlerMethod) handler).getMethodAnnotation(Idempotent.class);
        if (idem == null) {
            return true;
        }
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return true; // 未登录不参与防抖，交给登录拦截器处理
        }

        // key = idem:{userId}:{method}:{uri}
        String key = "idem:" + userId + ":" + request.getMethod() + ":" + request.getRequestURI();
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", idem.ttl(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            log.info("[Idempotent] 重复提交拦截: key={}", key);
            if (com.campus.bazaar.metrics.BizMetrics.idempotentRejected != null) {
                com.campus.bazaar.metrics.BizMetrics.idempotentRejected.increment();
            }
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"errorMsg\":\"" + idem.message() + "\"}");
            return false;
        }
        return true;
    }
}
