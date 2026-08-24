package com.campus.bazaar.config;

import cn.hutool.core.util.StrUtil;
import com.campus.bazaar.dto.UserDTO;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Token 刷新拦截器（双拦截器中的第一层，order=0）
 * <p>
 * 职责：
 * 1. 拦截所有请求，从 header 取 token；
 * 2. 校验通过则把 userId/昵称/头像写入 ThreadLocal（供后续业务使用）；
 * 3. <b>无感续期</b>：只要带着有效 token 访问，就把 Redis 里的 token TTL 重置，
 *    实现"只要在活跃期访问就永不掉线"，这就是简历里的"无感刷新"；
 * 4. token 不存在/无效时<b>不拦截</b>，直接放行，由第二层 LoginInterceptor 决定是否 401。
 */
@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 CORS 预检
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 1. 拿 token（header 优先，兼容 query param）
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            token = request.getParameter("token");
        }
        if (StrUtil.isBlank(token)) {
            // 未登录请求：放行，交给登录拦截器处理
            return true;
        }

        // 2. 从 Redis 还原 userId
        String userId = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_USER_KEY + token);
        if (userId == null) {
            // token 过期/无效：放行（LoginInterceptor 会返回 401），不注入用户
            return true;
        }

        // 3. 无感续期：重置 token 与 profile 的过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token + ":profile", RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 4. 读 profile，注入 ThreadLocal
        Map<Object, Object> profile = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token + ":profile");
        UserDTO dto = new UserDTO();
        dto.setId(Long.valueOf(userId));
        dto.setNickName(profile == null ? null : (String) profile.get("nickName"));
        dto.setIcon(profile == null ? "/imgs/icons/default-icon.svg" : (String) profile.get("icon"));
        UserHolder.setUserId(Long.valueOf(userId));
        UserHolder.setUser(dto);

        log.debug("[Auth] refresh token ok, userId={}, ttl 已续期", userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 统一清理 ThreadLocal，防止线程池复用导致用户串号
        UserHolder.clear();
    }
}
