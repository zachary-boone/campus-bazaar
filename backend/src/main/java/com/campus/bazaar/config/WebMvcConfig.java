package com.campus.bazaar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * Web MVC 配置 - 校园小黑市
 * <p>
 * 双拦截器注册（黑马点评模式）：
 * - {@link RefreshTokenInterceptor}（order=0）：拦全部路径，解析 token + 无感续期 + 注入 ThreadLocal，无效放行
 * - {@link LoginInterceptor}（order=1）：只拦需要登录的路径，ThreadLocal 无用户则 401
 * <p>
 * 白名单（不需要登录）：/user/code, /user/login, /user/info/** 等
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private RefreshTokenInterceptor refreshTokenInterceptor;

    @Resource
    private LoginInterceptor loginInterceptor;

    @Resource
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 0. 限流拦截器（优先级最高：先挡流量）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .order(0);

        // 1. Token 刷新拦截器：全路径，无 token 也放行
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(1);

        // 2. 登录校验拦截器：只拦截需要登录的接口
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/user/info/**",
                        "/error",
                        "/favicon.ico",
                        "/doc.html",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/v2/api-docs/**"
                )
                .order(2);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
