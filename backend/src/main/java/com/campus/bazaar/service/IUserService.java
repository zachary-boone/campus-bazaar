package com.campus.bazaar.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.bazaar.dto.LoginFormDTO;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.User;

/**
 * 用户服务接口（校园小黑市）
 */
public interface IUserService extends IService<User> {

    /** 发送手机验证码 -> 写入 Redis (login:code:{phone}, 2 分钟) */
    Result sendCode(String phone);

    /** 验证码登录：校验 + 查/建 user + 下发 token (Redis login:token:{token}, 10 小时) */
    Result loginByCode(LoginFormDTO form);

    /** 根据请求头 token 查询当前 user */
    Result me(String token);

    /** 退出登录 (移除 token) */
    Result logout(String token);
}
