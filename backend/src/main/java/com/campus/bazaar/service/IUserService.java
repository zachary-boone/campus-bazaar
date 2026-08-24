package com.campus.bazaar.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.bazaar.dto.LoginFormDTO;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.entity.UserInfo;

public interface IUserService extends IService<User> {

    /**
     * 发送手机验证码
     * @param phone 手机号
     * @return 验证码信息
     */
    Result sendCode(String phone);

    /**
     * 验证码登录/注册
     * @param form 登录表单
     * @return token
     */
    Result loginByCode(LoginFormDTO form);

    /**
     * 密码登录
     * @param form 登录表单
     * @return token
     */
    Result loginByPassword(LoginFormDTO form);

    /**
     * 获取当前用户信息
     * @param token token
     * @return 用户信息
     */
    Result me(String token);

    /**
     * 退出登录
     * @param token token
     * @return 操作结果
     */
    Result logout(String token);

    /**
     * 修改用户扩展信息
     * @param userInfo 用户信息
     * @return 操作结果
     */
    Result updateUserInfo(UserInfo userInfo);

    /**
     * 修改昵称
     * @param nickName 新昵称
     * @return 操作结果
     */
    Result updateNickName(String nickName);

    /**
     * 修改密码
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 操作结果
     */
    Result updatePassword(String oldPassword, String newPassword);
}
