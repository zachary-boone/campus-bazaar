package com.campus.bazaar.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.LoginFormDTO;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.service.IUserService;
import com.campus.bazaar.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 校园小黑市用户登录注册服务
 * <p>
 * 演示流程：
 * 1) sendCode 把随机生成的 6 位验证码写入 Redis，2 分钟有效；为方便体验，验证码会同时打印日志
 * 2) loginByCode 校验 Redis 中的验证码，通过后查表；若 phone 不存在则自动创建新用户，最后生成 UUID token 存 Redis 10 小时
 * 3) 前端用 axios 把 token 放入 header(authorization)，LoginInterceptor 从 Redis 中还原 user
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 全局兜底验证码：debug 模式下输入这串数字即可登录 (与动态生成的冲突时也认这一个，方便测试) */
    private static final String DEV_FIXED_CODE = "888888";

    @Override
    public Result sendCode(String phone) {
        // 1. 校验手机号格式
        if (phone == null || phone.length() != 11 || !phone.startsWith("1")) {
            return Result.fail("请输入正确的 11 位手机号");
        }
        // 2. 生成 6 位验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 写 Redis, 2 分钟过期
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES
        );
        // 4. 打印日志 (生产环境会接入短信通道)
        log.info("📱 [校园小黑市] 手机号 {} 的验证码是: {}", phone, code);

        // 5. 演示模式：把验证码和"开发固定码 888888"都返回，方便前端教学/调试
        Map<String, Object> data = new HashMap<>();
        data.put("phone", phone);
        data.put("code", code);            // 动态生成的 (生产模式可移除)
        data.put("devCode", "888888");     // 兜底码，永远可用
        data.put("expireMinutes", RedisConstants.LOGIN_CODE_TTL);
        data.put("tip", "demo：演示模式把验证码直接返回。生产环境接短信通道时 data 为空。");
        return Result.ok(data);
    }

    @Override
    public Result loginByCode(LoginFormDTO form) {
        // 1. 参数校验
        if (form.getPhone() == null || form.getCode() == null) {
            return Result.fail("手机号和验证码不能为空");
        }
        // 2. 从 Redis 取验证码
        String realCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + form.getPhone());
        // 3. 校验：与 Redis 中一致 OR 等于开发固定码 888888
        if (realCode == null) {
            return Result.fail("验证码已过期，请重新发送");
        }
        if (!realCode.equals(form.getCode()) && !DEV_FIXED_CODE.equals(form.getCode())) {
            return Result.fail("验证码错误");
        }

        // 4. 根据手机号查 user，不存在则创建
        User user = query().eq("phone", form.getPhone()).one();
        if (user == null) {
            user = new User();
            user.setPhone(form.getPhone());
            user.setNickName("同学_" + form.getPhone().substring(7));
            user.setIcon("/imgs/icons/default-icon.svg");
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            save(user);
            log.info("🆕 自动创建新用户: id={}, phone={}", user.getId(), user.getPhone());
        }

        // 5. 生成 token, 写 Redis (key=login:token:{token}, value=userId, TTL=10h)
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_USER_KEY + token,
                String.valueOf(user.getId()),
                RedisConstants.LOGIN_USER_TTL,
                TimeUnit.SECONDS
        );

        // 6. 清除该手机号验证码 (一次一用)
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + form.getPhone());

        // 7. 顺便把昵称/头像放到 Redis，方便拦截器还原 user 时减少一次 DB 查询
        Map<String, String> profile = new HashMap<>();
        profile.put("id", String.valueOf(user.getId()));
        profile.put("phone", user.getPhone());
        profile.put("nickName", user.getNickName() == null ? "" : user.getNickName());
        profile.put("icon", user.getIcon() == null ? "/imgs/icons/default-icon.svg" : user.getIcon());
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token + ":profile", profile);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token + ":profile",
                RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        log.info("✅ 登录成功: userId={}, phone={}, token={}********", user.getId(), user.getPhone(), token.substring(0, 8));
        return Result.ok(token);
    }

    @Override
    public Result me(String token) {
        if (token == null || token.isBlank()) {
            return Result.fail("未登录");
        }
        // 先从 hash 拿用户 profile (LoginInterceptor 已经塞过)
        Map<Object, Object> profile = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token + ":profile");
        if (profile == null || profile.isEmpty()) {
            return Result.fail("登录已过期，请重新登录");
        }
        // 兜底: 实时从 DB 再读一次
        String userIdStr = (String) profile.get("id");
        if (userIdStr == null) {
            return Result.fail("登录信息缺失");
        }
        User dbUser = getById(Long.valueOf(userIdStr));
        if (dbUser == null) {
            return Result.fail("用户不存在");
        }
        Map<String, Object> resp = BeanUtil.beanToMap(dbUser);
        // 简化返回字段（去掉敏感/无关字段）
        resp.put("id", dbUser.getId());
        resp.put("phone", dbUser.getPhone());
        resp.put("nickName", dbUser.getNickName());
        resp.put("icon", dbUser.getIcon());
        resp.put("createTime", dbUser.getCreateTime());
        resp.remove("password");
        return Result.ok(resp);
    }

    @Override
    public Result logout(String token) {
        if (token == null || token.isBlank()) {
            return Result.ok();
        }
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token + ":profile");
        return Result.ok();
    }
}
