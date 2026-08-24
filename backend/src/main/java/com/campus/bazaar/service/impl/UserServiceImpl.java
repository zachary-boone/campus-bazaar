package com.campus.bazaar.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.LoginFormDTO;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.entity.UserInfo;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.service.IUserInfoService;
import com.campus.bazaar.service.IUserService;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserInfoService userInfoService;

    /** 全局兜底验证码 */
    private static final String DEV_FIXED_CODE = "888888";

    @Override
    public Result sendCode(String phone) {
        // 1. 校验手机号格式
        if (phone == null || phone.length() != 11 || !phone.startsWith("1")) {
            return Result.fail("请输入正确的 11 位手机号");
        }
        // 2. 两级频率限制（ZSet 滑动窗口）：
        //    一级：60s 内最多 3 次（分钟级防刷）
        //    二级：1h 内最多 10 次（小时级风控）
        boolean minPass = checkCodeLimit(RedisConstants.CODE_LIMIT_MIN_KEY + phone,
                RedisConstants.CODE_LIMIT_MIN_TTL, RedisConstants.CODE_LIMIT_MIN_COUNT);
        if (!minPass) {
            return Result.fail("发送太频繁，请 1 分钟后再试");
        }
        boolean hourPass = checkCodeLimit(RedisConstants.CODE_LIMIT_HOUR_KEY + phone,
                RedisConstants.CODE_LIMIT_HOUR_TTL, RedisConstants.CODE_LIMIT_HOUR_COUNT);
        if (!hourPass) {
            return Result.fail("今日发送次数过多，请稍后再试");
        }

        // 3. 生成 6 位验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 存入 Redis, 2 分钟过期
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES
        );
        // 5. 打印日志
        log.info("📱 [校园小黑市] 手机号 {} 的验证码: {}", phone, code);

        // 6. 演示模式
        Map<String, Object> data = new HashMap<>();
        data.put("phone", phone);
        data.put("code", code);
        data.put("devCode", "888888");
        data.put("expireMinutes", RedisConstants.LOGIN_CODE_TTL);
        data.put("tip", "demo：演示模式把验证码直接返回。生产环境接短信通道，data 为空。");
        return Result.ok(data);
    }

    /**
     * ZSet 滑动窗口频率限制（原子 Lua）：
     * 清理窗口外记录 → 统计窗口内次数 → 未超限则记录本次并返回 true
     */
    private static final String CODE_LIMIT_LUA =
            "redis.call('zremrangebyscore', KEYS[1], 0, ARGV[1] - tonumber(ARGV[2]) * 1000) " +
            "local count = redis.call('zcard', KEYS[1]) " +
            "if count >= tonumber(ARGV[3]) then return 0 end " +
            "redis.call('zadd', KEYS[1], ARGV[1], ARGV[1] .. ':' .. math.random(1000000)) " +
            "redis.call('expire', KEYS[1], tonumber(ARGV[2]) + 5) " +
            "return 1";

    private boolean checkCodeLimit(String key, long windowSec, int maxCount) {
        org.springframework.data.redis.core.script.DefaultRedisScript<Long> script =
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(CODE_LIMIT_LUA, Long.class);
        Long result = stringRedisTemplate.execute(script, java.util.Collections.singletonList(key),
                String.valueOf(System.currentTimeMillis()), String.valueOf(windowSec), String.valueOf(maxCount));
        return result != null && result == 1L;
    }

    @Override
    public Result loginByCode(LoginFormDTO form) {
        // 1. 参数校验
        if (form.getPhone() == null || form.getCode() == null) {
            return Result.fail("手机号和验证码不能为空");
        }
        // 2. 从 Redis 取验证码
        String realCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + form.getPhone());
        // 3. 校验
        if (realCode == null) {
            return Result.fail("验证码已过期，请重新发送");
        }
        if (!realCode.equals(form.getCode()) && !DEV_FIXED_CODE.equals(form.getCode())) {
            return Result.fail("验证码错误");
        }

        // 4. 根据手机号查 user，不存在则创建
        User user = query().eq("phone", form.getPhone()).one();
        if (user == null) {
            user = createUser(form.getPhone());
        }

        // 5. 生成 token, 存入 Redis
        String token = createToken(user);

        // 6. 清除验证码
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + form.getPhone());

        return Result.ok(token);
    }

    @Override
    public Result loginByPassword(LoginFormDTO form) {
        // 1. 参数校验
        if (StrUtil.isBlank(form.getPhone()) || StrUtil.isBlank(form.getPassword())) {
            return Result.fail("手机号和密码不能为空");
        }

        // 2. 根据手机号查询用户
        User user = query().eq("phone", form.getPhone()).one();
        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 3. 校验密码
        String inputPassword = DigestUtils.md5DigestAsHex(form.getPassword().getBytes());
        if (!inputPassword.equals(user.getPassword())) {
            return Result.fail("密码错误");
        }

        // 4. 生成 token
        String token = createToken(user);

        return Result.ok(token);
    }

    @Override
    public Result me(String token) {
        if (token == null || token.isBlank()) {
            return Result.fail("未登录");
        }
        // 从 hash 拿用户 profile
        Map<Object, Object> profile = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token + ":profile");
        if (profile == null || profile.isEmpty()) {
            return Result.fail("登录已过期，请重新登录");
        }
        String userIdStr = (String) profile.get("id");
        if (userIdStr == null) {
            return Result.fail("登录信息缺失");
        }
        User dbUser = getById(Long.valueOf(userIdStr));
        if (dbUser == null) {
            return Result.fail("用户不存在");
        }
        Map<String, Object> resp = BeanUtil.beanToMap(dbUser);
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

    @Override
    @Transactional
    public Result updateUserInfo(UserInfo userInfo) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 2. 设置用户ID
        userInfo.setUserId(userId);
        userInfo.setUpdateTime(LocalDateTime.now());

        // 3. 判断是新增还是更新
        UserInfo existingInfo = userInfoService.getById(userId);
        if (existingInfo == null) {
            userInfo.setCreateTime(LocalDateTime.now());
            userInfoService.save(userInfo);
        } else {
            userInfoService.updateById(userInfo);
        }

        return Result.ok();
    }

    @Override
    @Transactional
    public Result updateNickName(String nickName) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 2. 校验昵称
        if (StrUtil.isBlank(nickName) || nickName.length() > 32) {
            return Result.fail("昵称长度不能超过32个字符");
        }

        // 3. 更新昵称
        User user = new User();
        user.setId(userId);
        user.setNickName(nickName);
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);

        // 4. 更新Redis中的缓存
        String token = UserHolder.getUser() != null ? null : null; // 需要从其他地方获取token
        // 注意：这里简化处理，实际应该更新Redis缓存

        return Result.ok();
    }

    @Override
    @Transactional
    public Result updatePassword(String oldPassword, String newPassword) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 2. 校验参数
        if (StrUtil.isBlank(oldPassword) || StrUtil.isBlank(newPassword)) {
            return Result.fail("密码不能为空");
        }
        if (newPassword.length() < 6 || newPassword.length() > 20) {
            return Result.fail("密码长度需要在6-20位之间");
        }

        // 3. 查询用户
        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 4. 校验旧密码
        String oldPwd = DigestUtils.md5DigestAsHex(oldPassword.getBytes());
        if (!oldPwd.equals(user.getPassword())) {
            return Result.fail("旧密码错误");
        }

        // 5. 更新密码
        String newPwd = DigestUtils.md5DigestAsHex(newPassword.getBytes());
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setPassword(newPwd);
        updateUser.setUpdateTime(LocalDateTime.now());
        updateById(updateUser);

        return Result.ok();
    }

    /**
     * 创建新用户
     */
    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("同学_" + phone.substring(7));
        user.setIcon("/imgs/icons/default-icon.svg");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        save(user);
        log.info("🆕 自动创建新用户: id={}, phone={}", user.getId(), user.getPhone());
        return user;
    }

    /**
     * 生成token并存入Redis
     */
    private String createToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_USER_KEY + token,
                String.valueOf(user.getId()),
                RedisConstants.LOGIN_USER_TTL,
                TimeUnit.SECONDS
        );

        // 把昵称头像放到 Redis
        Map<String, String> profile = new HashMap<>();
        profile.put("id", String.valueOf(user.getId()));
        profile.put("phone", user.getPhone());
        profile.put("nickName", user.getNickName() == null ? "" : user.getNickName());
        profile.put("icon", user.getIcon() == null ? "/imgs/icons/default-icon.svg" : user.getIcon());
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token + ":profile", profile);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token + ":profile",
                RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        log.info("✅ 登录成功: userId={}, phone={}, token={}********", user.getId(), user.getPhone(), token.substring(0, 8));
        return token;
    }
}
