package com.campus.bazaar.config;

import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录校验拦截器（双拦截器中的第二层，order=1）
 * <p>
 * 只负责一件事：<b>校验登录态</b>。
 * token 的解析、续期、注入已由 {@link RefreshTokenInterceptor} 完成，
 * 本拦截器只需要看 ThreadLocal 里有没有用户，没有就返回 401。
 * 由 WebMvcConfig 通过 pathPatterns 控制它只拦截需要登录的接口。
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 用户已由刷新拦截器注入 → 放行
        if (UserHolder.getUser() != null) {
            return true;
        }
        // 未登录
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"errorMsg\":\"请先登录\"}");
        return false;
    }

    // ThreadLocal 由 RefreshTokenInterceptor.afterCompletion 统一清理，这里不再重复 clear
}
