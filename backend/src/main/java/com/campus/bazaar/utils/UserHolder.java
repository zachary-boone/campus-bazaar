package com.campus.bazaar.utils;

import com.campus.bazaar.dto.UserDTO;

/**
 * 用户上下文 - ThreadLocal 工具
 * <p>
 * LoginInterceptor 把从 Redis 还原出的 userId/nickName/icon 存进来，
 * 同一次请求的任何地方都可以拿到（Controller / Service / 拦截器）
 */
public class UserHolder {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<UserDTO> USER = new ThreadLocal<>();

    public static void setUserId(Long id) { USER_ID.set(id); }
    public static Long getUserId() { return USER_ID.get(); }

    public static void setUser(UserDTO user) { USER.set(user); }
    public static UserDTO getUser() { return USER.get(); }

    /** 必须在拦截器 afterCompletion 里清掉，防止内存泄漏 */
    public static void clear() {
        USER_ID.remove();
        USER.remove();
    }
}
