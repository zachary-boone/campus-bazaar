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

/**
 * 登录拦截器 - 校园小黑市
 * <p>
 * 从请求头 `authorization` 拿 token；
 * 从 Redis (`login:token:{token}`) 还原 userId；
 * 注入到 {@link UserHolder}，请求内任何位置都能拿到。
 * <p>
 * 注：本期只做单拦截器。黑马教程讲的"刷新拦截器 + 登录拦截器"双拦截器
 * 通过"分别配 pathPatterns + 拦截顺序 + 共享 ThreadLocal"实现，
 * 实际工作流一致；后续如要刷新 token，可加一个 RefreshTokenInterceptor 优先级更低。
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 放行 OPTIONS (CORS 预检)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 2. 拿 token (header 优先，再尝试 query param 兼容老接口)
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            token = request.getParameter("token");
        }
        if (StrUtil.isBlank(token)) {
            // 没登录的请求：直接放行 (拦截器由 WebMvcConfig 配 pathPatterns 决定拦截谁)
            return true;
        }

        // 3. 从 Redis 还原 userId
        String userId = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_USER_KEY + token);
        if (userId == null) {
            // token 过期或无效
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"errorMsg\":\"登录已过期，请重新登录\"}");
            return false;
        }

        // 4. 顺便把昵称读出来 (offline profile hash)
        Map<Object, Object> profile = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token + ":profile");
        String nickName = profile == null ? null : (String) profile.get("nickName");

        // 5. 注入 ThreadLocal
        UserHolder.setUserId(Long.valueOf(userId));
        UserDTO dto = new UserDTO();
        dto.setId(Long.valueOf(userId));
        dto.setNickName(nickName);
        dto.setIcon(profile == null ? "/imgs/icons/default-icon.svg" : (String) profile.get("icon"));
        UserHolder.setUser(dto);

        log.debug("[Auth] userId={}, nickName={} ok", userId, nickName);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.clear();
    }
}
