package com.campus.bazaar.controller;

import cn.hutool.core.util.StrUtil;
import com.campus.bazaar.dto.LoginFormDTO;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.dto.UserDTO;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.entity.UserInfo;
import com.campus.bazaar.service.IUserInfoService;
import com.campus.bazaar.service.IUserService;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Slf4j
@Validated
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /** 发送手机验证码 - 无需登录（令牌桶限流 + ZSet 两级频率限制） */
    @PostMapping("/code")
    @com.campus.bazaar.utils.RateLimit(key = "code", rate = 2, capacity = 5,
            message = "验证码发送过于频繁，请稍后再试")
    public Result sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /** 验证码登录/注册 - 无需登录 */
    @PostMapping("/login")
    public Result login(@Valid @RequestBody LoginFormDTO form) {
        return userService.loginByCode(form);
    }

    /** 密码登录 - 无需登录 */
    @PostMapping("/login/password")
    public Result loginByPassword(@Valid @RequestBody LoginFormDTO form) {
        return userService.loginByPassword(form);
    }

    /** 当前用户信息 - 必须登录 */
    @GetMapping("/me")
    public Result me() {
        Long uid = UserHolder.getUserId();
        if (uid == null) {
            return Result.fail("未登录，请先登录");
        }
        User u = userService.getById(uid);
        if (u == null) return Result.fail("用户不存在");
        u.setPassword(null);
        return Result.ok(u);
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest req) {
        String token = req.getHeader("authorization");
        return userService.logout(token);
    }

    /** 用户详情页（供他主页用） */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return Result.ok(info);
    }

    /** 修改用户基本信息 - 必须登录 */
    @PutMapping("/info")
    public Result updateUserInfo(@RequestBody UserInfo userInfo) {
        return userService.updateUserInfo(userInfo);
    }

    /** 修改用户昵称 - 必须登录 */
    @PutMapping("/nickName")
    public Result updateNickName(@RequestParam("nickName") String nickName) {
        return userService.updateNickName(nickName);
    }

    /** 修改密码 - 必须登录 */
    @PutMapping("/password")
    public Result updatePassword(@RequestParam("oldPassword") String oldPassword,
                                 @RequestParam("newPassword") String newPassword) {
        return userService.updatePassword(oldPassword, newPassword);
    }
}
