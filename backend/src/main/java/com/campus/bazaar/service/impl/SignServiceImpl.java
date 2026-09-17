package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Coupon;
import com.campus.bazaar.entity.CouponOrder;
import com.campus.bazaar.entity.Sign;
import com.campus.bazaar.mapper.SignMapper;
import com.campus.bazaar.service.ICouponOrderService;
import com.campus.bazaar.service.ICouponService;
import com.campus.bazaar.service.ISignService;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 每日签到（Redis Bitmap 实现）
 * <p>
 * 存储设计：
 * <ul>
 *   <li>Bitmap：{@code sign:{userId}:{yyyyMM}}，bit offset = 当月第几天 - 1，一个月最多 31 bit（约 4 字节）。
 *       签到 = SETBIT（O(1)）；查记录 / 算连续天数 = 逐天 GETBIT（见 {@link #monthBits} 里关于
 *       本机 Redis BITFIELD 位序不自洽的说明）；<= 2 个月的读取量，与该月签到天数无关。</li>
 *   <li>兜底表：{@code tb_sign}，唯一键 uk_user_date 保证同一天不重复入库，Redis 数据丢失时可重建。</li>
 * </ul>
 * 奖励规则：连续签到每满 {@link RedisConstants#SIGN_WEEK_DAYS} 天，获得 1 张运费券的领取资格，
 * 用户在签到页手动领取（earned 累计、claimed 已领取，可领取数 = earned - claimed）。
 */
@Slf4j
@Service
public class SignServiceImpl implements ISignService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SignMapper signMapper;

    @Resource
    private ICouponService couponService;

    @Resource
    private ICouponOrderService couponOrderService;

    /** 签到奖励发放的运费券模板 id，见 db/upgrade-0917-sign-coupon.sql */
    public static final long SIGN_COUPON_ID = 9001L;

    /** 连续天数回溯上限，防御脏数据导致的超长循环（覆盖一整年） */
    private static final int MAX_STREAK_LOOKBACK = 400;

    /** bitmap key 的月份后缀：sign:{userId}:202609 */
    private static final DateTimeFormatter MONTH_SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");

    // ==================================================================
    //  对外接口
    // ==================================================================

    @Override
    public Result sign() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        LocalDate today = LocalDate.now();
        int offset = today.getDayOfMonth() - 1;

        // 1. 原子签到判重：SETBIT 返回该位"原来的值"，为 1 说明今天已经签过。
        //    Redis 单线程保证 SETBIT 原子，因此并发连点也只有一个请求能拿到 0，
        //    从根上避免"两个请求都判重通过 → 7 天周期被重复发券"的问题。
        Boolean signedBefore = stringRedisTemplate.opsForValue()
                .setBit(monthKey(userId, today), offset, true);
        if (Boolean.TRUE.equals(signedBefore)) {
            return Result.fail("今日已签到，明天再来~");
        }

        // 2. 续期 62 天：跨月统计连续签到需要读上月 bit，同时防止 key 无限堆积
        stringRedisTemplate.expire(monthKey(userId, today),
                RedisConstants.SIGN_BITMAP_TTL_DAYS, TimeUnit.DAYS);

        // 3. 落库兜底：tb_sign 的唯一键 uk_user_date 兜住并发/重复写入
        try {
            signMapper.insert(new Sign()
                    .setUserId(userId)
                    .setYear(today.getYear())
                    .setMonth(today.getMonthValue())
                    .setDate(today)
                    .setCreateTime(LocalDateTime.now()));
        } catch (DuplicateKeyException e) {
            log.warn("[Sign] 重复签到已忽略(唯一键拦截): userId={}, date={}", userId, today);
        }

        // 4. 统计连续天数（含今天）；每满 7 天累加 1 张运费券的领取资格
        int streak = countStreak(userId, today);
        boolean rewarded = streak > 0 && streak % RedisConstants.SIGN_WEEK_DAYS == 0;
        if (rewarded) {
            stringRedisTemplate.opsForValue()
                    .increment(RedisConstants.SIGN_WEEK_EARNED_KEY + userId);
            log.info("[Sign] 连续签到满{}天，发放运费券领取资格: userId={}, streak={}",
                    RedisConstants.SIGN_WEEK_DAYS, userId, streak);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("streak", streak);
        data.put("todaySigned", true);
        data.put("rewarded", rewarded);
        data.put("nextRewardDays", daysToNextReward(streak));
        data.put("claimable", claimableCount(userId));
        return Result.ok(data);
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(currentStreak(userId));
    }

    @Override
    public Result signRecords(Integer year, Integer month) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        LocalDate today = LocalDate.now();
        // 1. 默认查本月；只允许查询当月及以前（未来月份没有记录）
        int y = year == null ? today.getYear() : year;
        int m = month == null ? today.getMonthValue() : month;
        if (m < 1 || m > 12) {
            return Result.fail("月份不合法");
        }
        LocalDate firstDay = LocalDate.of(y, m, 1);
        if (firstDay.isAfter(today.withDayOfMonth(1))) {
            return Result.fail("不能查看未来的签到记录");
        }

        // 2. 读取整月 bitmap，解析出已签日期
        int daysInMonth = firstDay.lengthOfMonth();
        boolean[] signed = monthBits(userId, firstDay);
        List<Integer> signedDays = new ArrayList<>();
        for (int d = 1; d <= daysInMonth; d++) {
            if (signed[d - 1]) {
                signedDays.add(d);
            }
        }

        boolean isCurrentMonth = (y == today.getYear() && m == today.getMonthValue());
        int streak = currentStreak(userId);
        long earned = readCounter(RedisConstants.SIGN_WEEK_EARNED_KEY + userId);
        long claimed = readCounter(RedisConstants.SIGN_WEEK_CLAIMED_KEY + userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("year", y);
        data.put("month", m);
        data.put("isCurrentMonth", isCurrentMonth);
        data.put("daysInMonth", daysInMonth);
        data.put("firstWeekday", firstDay.getDayOfWeek().getValue() % 7); // 0=周日，前端日历排布用
        data.put("signedDays", signedDays);
        data.put("signedCount", signedDays.size());
        data.put("todaySigned", isCurrentMonth && signedDays.contains(today.getDayOfMonth()));
        data.put("streak", streak);
        data.put("nextRewardDays", daysToNextReward(streak));
        data.put("weekDays", RedisConstants.SIGN_WEEK_DAYS);
        data.put("claimable", Math.max(0L, earned - claimed));
        data.put("totalEarned", earned);
        data.put("totalClaimed", claimed);
        data.put("coupon", couponBrief());
        return Result.ok(data);
    }

    @Override
    public Result signCoupons() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        // 已领取的签到运费券（status = -1 的超时单不计入）
        List<CouponOrder> orders = couponOrderService.list(new QueryWrapper<CouponOrder>()
                .eq("user_id", userId)
                .eq("coupon_id", SIGN_COUPON_ID)
                .ne("status", -1)
                .orderByDesc("create_time"));
        Coupon coupon = couponService.getById(SIGN_COUPON_ID);

        List<Map<String, Object>> list = new ArrayList<>();
        for (CouponOrder order : orders) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", order.getId());
            item.put("status", order.getStatus());
            item.put("createTime", order.getCreateTime() == null ? null : order.getCreateTime().toString());
            item.put("title", coupon == null ? "签到运费券" : coupon.getTitle());
            item.put("subTitle", coupon == null ? null : coupon.getSubTitle());
            item.put("rules", coupon == null ? null : coupon.getRules());
            item.put("actualValue", coupon == null ? null : coupon.getActualValue());
            list.add(item);
        }
        return Result.ok(list);
    }

    @Override
    public Result claimWeekCoupon() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 1. 原子占位：先自增"已领取数"，再与"已获得数"比较；
        //    超出说明没有可领取额度，立即回滚（把并发领取收敛为 Redis 的一次 INCR 判定）
        String claimedKey = RedisConstants.SIGN_WEEK_CLAIMED_KEY + userId;
        Long claimed = stringRedisTemplate.opsForValue().increment(claimedKey);
        long earned = readCounter(RedisConstants.SIGN_WEEK_EARNED_KEY + userId);
        if (claimed == null || claimed > earned) {
            stringRedisTemplate.opsForValue().decrement(claimedKey);
            return Result.fail("暂无可领取的运费券，连续签到满 "
                    + RedisConstants.SIGN_WEEK_DAYS + " 天即可获得");
        }

        try {
            // 2. 校验券模板是否已配置
            Coupon coupon = couponService.getById(SIGN_COUPON_ID);
            if (coupon == null) {
                rollbackClaim(claimedKey);
                return Result.fail("签到运费券暂未配置，请联系管理员");
            }

            // 3. 发券：写一张"已领取"状态的券订单（0 元赠券不涉及支付，status 直接置 1 表示可用）
            CouponOrder order = new CouponOrder();
            order.setId(genOrderId());
            order.setUserId(userId);
            order.setCouponId(SIGN_COUPON_ID);
            order.setStatus(1);
            order.setPayType(0);
            order.setPayTime(LocalDateTime.now());
            order.setCreateTime(LocalDateTime.now());
            couponOrderService.save(order);

            log.info("[Sign] 签到运费券领取成功: userId={}, orderId={}, 剩余可领取={}",
                    userId, order.getId(), earned - claimed);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderId", order.getId());
            data.put("title", coupon.getTitle());
            data.put("subTitle", coupon.getSubTitle());
            data.put("rules", coupon.getRules());
            data.put("actualValue", coupon.getActualValue());
            data.put("claimable", Math.max(0L, earned - claimed));
            return Result.ok(data);
        } catch (Exception e) {
            // 落库失败必须归还额度，否则用户会凭空少一张券
            rollbackClaim(claimedKey);
            log.error("[Sign] 签到运费券领取失败: userId={}, err={}", userId, e.getMessage(), e);
            return Result.fail("领取失败，请稍后重试");
        }
    }

    // ==================================================================
    //  Bitmap 读写与连续天数统计
    // ==================================================================

    /** 月bitmap key：sign:{userId}:{yyyyMM} */
    private String monthKey(Long userId, LocalDate date) {
        return RedisConstants.USER_SIGN_KEY + userId + ":" + date.format(MONTH_SUFFIX);
    }

    /** 读取某一天的签到位 */
    private Boolean getBit(Long userId, LocalDate date) {
        return stringRedisTemplate.opsForValue()
                .getBit(monthKey(userId, date), date.getDayOfMonth() - 1);
    }

    /**
     * 读取某月的签到位图（下标 0 = 1 号）。
     * <p>
     * <b>为什么逐天用 GETBIT，而不是一次 BITFIELD GET uN</b>：
     * 本机 Windows 版 Redis（5.0.14.1）的 BITFIELD 与 SETBIT/GETBIT 位序不自洽——
     * 对照实验：{@code SETBIT k 16 1} 写入字节 0x80，{@code GETBIT k 16} 读回 1（两者一致），
     * 但 {@code BITFIELD k GET u31 0} 返回 2^14、{@code GET u30 0} 返回 2^13，
     * 按它解析整月记录会整体错位（表现为签到日期错位、连续天数算错）。
     * GETBIT 与写入用的 SETBIT 出自同一套位序，天然一致；
     * 而且换成标准 Redis（Linux/Docker，字节内 LSB 优先）也不会因字节序差异失效。
     * 代价是一个月最多 31 次 GETBIT，本地约 1ms，签到属低频操作，完全可接受。
     */
    private boolean[] monthBits(Long userId, LocalDate anyDayOfMonth) {
        int days = anyDayOfMonth.lengthOfMonth();
        String key = monthKey(userId, anyDayOfMonth);
        boolean[] signed = new boolean[days];
        for (int d = 1; d <= days; d++) {
            signed[d - 1] = Boolean.TRUE.equals(
                    stringRedisTemplate.opsForValue().getBit(key, d - 1));
        }
        return signed;
    }

    /**
     * 连续签到天数（当前视角）：今天签了就从今天往前数；今天没签但昨天签了，
     * 连续记录仍"活着"，展示到昨天为止的连续天数；今天昨天都没签则为 0。
     */
    private int currentStreak(Long userId) {
        LocalDate today = LocalDate.now();
        if (Boolean.TRUE.equals(getBit(userId, today))) {
            return countStreak(userId, today);
        }
        LocalDate yesterday = today.minusDays(1);
        if (Boolean.TRUE.equals(getBit(userId, yesterday))) {
            return countStreak(userId, yesterday);
        }
        return 0;
    }

    /**
     * 统计"截止到 endDate（含）"的连续签到天数，支持跨月回溯：
     * 本月从 endDate 逐天向前扫；若一直连续到本月 1 号，则继续读上月位图，
     * 因此最多读 2 个月，不会随连续天数线性放大查询量。
     */
    private int countStreak(Long userId, LocalDate endDate) {
        int streak = 0;
        LocalDate cursor = endDate;
        while (streak < MAX_STREAK_LOOKBACK) {
            boolean[] signed = monthBits(userId, cursor);
            int fromDay = Math.min(cursor.getDayOfMonth(), signed.length);
            int hit = 0;
            for (int d = fromDay; d >= 1; d--) {
                if (signed[d - 1]) {
                    hit++;
                } else {
                    break;
                }
            }
            streak += hit;
            if (hit < fromDay) {
                break; // 本月内已断签，无需再看上月
            }
            cursor = cursor.withDayOfMonth(1).minusDays(1); // 跳到上月最后一天
        }
        return streak;
    }

    /** 距离下一次获得运费券还差几天（满 7 天为一个周期） */
    private int daysToNextReward(int streak) {
        int rem = streak % RedisConstants.SIGN_WEEK_DAYS;
        return rem == 0 ? RedisConstants.SIGN_WEEK_DAYS : RedisConstants.SIGN_WEEK_DAYS - rem;
    }

    // ==================================================================
    //  奖励额度与工具方法
    // ==================================================================

    /** 可领取运费券张数 = 累计获得 - 已领取 */
    private int claimableCount(Long userId) {
        long earned = readCounter(RedisConstants.SIGN_WEEK_EARNED_KEY + userId);
        long claimed = readCounter(RedisConstants.SIGN_WEEK_CLAIMED_KEY + userId);
        return (int) Math.max(0L, earned - claimed);
    }

    /** 归还一次领取占位（发券失败时调用） */
    private void rollbackClaim(String claimedKey) {
        stringRedisTemplate.opsForValue().decrement(claimedKey);
    }

    private long readCounter(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("[Sign] 计数器值异常，按 0 处理: key={}, value={}", key, value);
            return 0L;
        }
    }

    /** 券订单 id：毫秒时间戳 + 3 位随机数，避免秒杀链路用时间戳时同毫秒撞号 */
    private long genOrderId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    /** 券模板摘要（页面展示领取规则用；未配置时返回 null 不阻断签到） */
    private Map<String, Object> couponBrief() {
        Coupon coupon = couponService.getById(SIGN_COUPON_ID);
        if (coupon == null) {
            return null;
        }
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("id", coupon.getId());
        brief.put("title", coupon.getTitle());
        brief.put("subTitle", coupon.getSubTitle());
        brief.put("rules", coupon.getRules());
        brief.put("actualValue", coupon.getActualValue());
        return brief;
    }
}
