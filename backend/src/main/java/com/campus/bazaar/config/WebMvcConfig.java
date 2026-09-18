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

    @Resource
    private IdempotentInterceptor idempotentInterceptor;

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
                        "/user/login/password",
                        // 仅放行 GET /user/info/{id}（查看他人主页）；/user/info 的 PUT（修改资料）需登录
                        "/user/info/*",
                        "/user/card/*",                   // 用户卡片（他人主页：昵称/头像/计数）
                        "/user/sellers",                  // 学长学姐专区：卖家列表
                        "/goods/of/seller/*",             // 某卖家的在售商品
                        "/post/of/user/*",                // 某用户发布的帖子
                        // ===== 校园小黑市公开接口（无需登录） =====
                        "/goods/category/list",          // 商品分类
                        "/goods/of/type",                 // 按分类查商品
                        "/goods/of/name",                 // 按名称搜索
                        "/goods/of/nearby",               // 附近商品
                        "/goods/map",                     // 地图页数据（中心点+半径内商品+校区统计）
                        "/goods/hot",                     // 热榜
                        "/goods/{id}",                     // 商品详情
                        "/post/hot",                      // 热门帖子
                        "/post/{id}",                     // 帖子详情
                        "/post/list/{goodsId}",           // 商品关联帖子
                        "/post/count",                    // 帖子统计
                        "/post/likes/{id}",               // 点赞列表
                        "/post/comments/{id}",            // 评论列表
                        "/post/like/status/{id}",         // 是否点赞
                        "/coupon/list/{goodsId}",         // 优惠券列表
                        "/coupon/{id}",                   // 券详情
                        "/coupon/seckill/{id}",           // 秒杀状态
                        "/follow/counts/*",               // 关注数/粉丝数（个人中心、他人主页）
                        "/follow/following/*",            // TA 关注的人
                        "/follow/followers/*",            // TA 的粉丝
                        "/group/active",                  // 拼单大厅（进行中的拼单列表）
                        "/group/goods/*",                 // 某商品的进行中拼单（详情页参团入口）
                        "/common/{id}",                   // 公共图片
                        "/error",
                        "/favicon.ico",
                        "/doc.html",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/v2/api-docs/**"
                )
                .order(2);

        // 3. 接口防重复提交（需要登录态，放在登录校验之后）
        registry.addInterceptor(idempotentInterceptor)
                .addPathPatterns("/**")
                .order(3);
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
