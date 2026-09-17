package com.campus.bazaar.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.LoginFormDTO;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.dto.UserCardDTO;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.entity.UserInfo;
import com.campus.bazaar.mapper.GoodsMapper;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.service.IFollowService;
import com.campus.bazaar.service.IUserInfoService;
import com.campus.bazaar.service.IUserService;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Resource
    private IFollowService followService;

    @Resource
    private GoodsMapper goodsMapper;

    /** BCrypt 密码加密器（自带随机盐，替代裸 MD5） */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /** 全局兜底验证码 */
    private static final String DEV_FIXED_CODE = "888888";

    /** tb_goods.status：1在售 / 2已售 / 3下架 / 4交易中 */
    private static final int GOODS_STATUS_ON_SALE = 1;
    private static final int GOODS_STATUS_SOLD = 2;

    /** 学长学姐专区单页最多返回的卖家数 */
    private static final int MAX_SELLER_LIST_SIZE = 50;

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

        // 3. 校验密码（BCrypt：自带随机盐，抗彩虹表）
        if (!PASSWORD_ENCODER.matches(form.getPassword(), user.getPassword())) {
            return Result.fail("密码错误");
        }

        // 4. 生成 token
        String token = createToken(user);

        return Result.ok(token);
    }

    @Override
    public Result me(String token) {
        if (StrUtil.isBlank(token)) {
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
        if (StrUtil.isBlank(token)) {
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
        if (StrUtil.isBlank(newPassword)) {
            return Result.fail("新密码不能为空");
        }
        if (newPassword.length() < 6 || newPassword.length() > 20) {
            return Result.fail("密码长度需要在6-20位之间");
        }

        // 3. 查询用户
        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 4. 校验旧密码（BCrypt；未设置过密码时允许直接设置）
        if (StrUtil.isNotBlank(user.getPassword())) {
            if (!PASSWORD_ENCODER.matches(oldPassword, user.getPassword())) {
                return Result.fail("旧密码错误");
            }
        }

        // 5. 更新密码（BCrypt 加密存储，自带盐）
        String newPwd = PASSWORD_ENCODER.encode(newPassword);
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

    @Override
    public Result userCard(Long userId) {
        if (userId == null) {
            return Result.fail("参数不合法");
        }
        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        UserCardDTO card = new UserCardDTO();
        card.setId(user.getId());
        card.setNickName(user.getNickName());
        card.setIcon(user.getIcon());
        // 签名存在 tb_user_info，可能还没填过
        UserInfo info = userInfoService.getById(userId);
        card.setIntroduce(info == null ? null : info.getIntroduce());
        // 计数以 tb_follow 实时算出（tb_user_info.fans/followee 是历史冗余列，一直没人维护，不作为数据源）
        card.setFollowers(followService.countFollowers(userId));
        card.setFollowing(followService.countFollowing(userId));
        // 在售 / 已售商品数（tb_goods: 1在售 2已售 3下架 4交易中）
        card.setGoodsCount(countGoods(userId, GOODS_STATUS_ON_SALE));
        card.setSoldCount(countGoods(userId, GOODS_STATUS_SOLD));
        // 是否已关注：未登录时 isFollow 返回 false，前端据此提示登录
        card.setFollowed(followService.isFollow(userId));
        return Result.ok(card);
    }

    @Override
    public Result sellerList(Integer current) {
        int pageNo = (current == null || current < 1) ? 1 : current;
        int offset = (pageNo - 1) * MAX_SELLER_LIST_SIZE;

        // 1. 一次 GROUP BY 取"每个卖家的在售/已售商品数"，只保留还有在售商品的卖家
        QueryWrapper<Goods> wrapper = new QueryWrapper<>();
        wrapper.select("seller_id",
                        "SUM(CASE WHEN status = " + GOODS_STATUS_ON_SALE + " THEN 1 ELSE 0 END) AS goods_count",
                        "SUM(CASE WHEN status = " + GOODS_STATUS_SOLD + " THEN 1 ELSE 0 END) AS sold_count")
                .groupBy("seller_id")
                .having("SUM(CASE WHEN status = " + GOODS_STATUS_ON_SALE + " THEN 1 ELSE 0 END) > 0")
                .orderByDesc("goods_count")
                .last("LIMIT " + offset + ", " + MAX_SELLER_LIST_SIZE);
        List<Map<String, Object>> rows = goodsMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2. 按聚合结果顺序取卖家 id
        List<Long> sellerIds = new ArrayList<>();
        Map<Long, long[]> statsMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object idObj = row.get("seller_id");
            if (!(idObj instanceof Number)) {
                continue;
            }
            long sellerId = ((Number) idObj).longValue();
            long goodsCount = num(row.get("goods_count"));
            long soldCount = num(row.get("sold_count"));
            sellerIds.add(sellerId);
            statsMap.put(sellerId, new long[]{goodsCount, soldCount});
        }
        if (sellerIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 3. 批量回填用户资料 / 签名 / 粉丝数（各一次查询，不做逐条 N+1）
        List<User> users = listByIds(sellerIds);
        Map<Long, User> userMap = new LinkedHashMap<>();
        for (User user : users) {
            userMap.put(user.getId(), user);
        }
        Map<Long, String> introduceMap = new LinkedHashMap<>();
        List<UserInfo> infos = userInfoService.listByIds(sellerIds);
        if (infos != null) {
            for (UserInfo info : infos) {
                introduceMap.put(info.getUserId(), info.getIntroduce());
            }
        }
        Map<Long, Long> fansMap = followService.countFollowersBatch(sellerIds);

        // 4. 按"在售商品数"的顺序组装（顺带过滤已注销用户）
        List<UserCardDTO> cards = new ArrayList<>();
        for (Long sellerId : sellerIds) {
            User seller = userMap.get(sellerId);
            if (seller == null) {
                continue;
            }
            long[] stats = statsMap.get(sellerId);
            UserCardDTO card = new UserCardDTO();
            card.setId(seller.getId());
            card.setNickName(seller.getNickName());
            card.setIcon(seller.getIcon());
            card.setIntroduce(introduceMap.get(sellerId));
            card.setGoodsCount(stats[0]);
            card.setSoldCount(stats[1]);
            card.setFollowers(fansMap.getOrDefault(sellerId, 0L));
            card.setFollowed(followService.isFollow(sellerId));
            cards.add(card);
        }
        return Result.ok(cards);
    }

    /** 统计某卖家的商品数（按状态） */
    private long countGoods(Long sellerId, int status) {
        // MP 3.4.x 的 selectCount 返回 Integer
        Integer count = goodsMapper.selectCount(new QueryWrapper<Goods>()
                .eq("seller_id", sellerId)
                .eq("status", status));
        return count == null ? 0L : count.longValue();
    }

    /** 聚合结果里的数值列可能被驱动返回成 BigDecimal / Long，统一转 long */
    private long num(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
