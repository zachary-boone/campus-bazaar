package com.campus.bazaar.service.impl;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Sign;
import com.campus.bazaar.mapper.SignMapper;
import com.campus.bazaar.service.ISignService;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SignServiceImpl implements ISignService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SignMapper signMapper;

    @Override
    public Result sign() {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();

        // 2. 获取日期
        LocalDate now = LocalDate.now();

        // 3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;

        // 4. 获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5. 写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);

        // 6. 写入数据库
        Sign sign = new Sign();
        sign.setUserId(userId);
        sign.setYear(now.getYear());
        sign.setMonth(now.getMonthValue());
        sign.setDate(now);
        sign.setCreateTime(LocalDateTime.now());
        signMapper.insert(sign);

        return Result.ok("签到成功");
    }

    @Override
    public Result signCount() {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();

        // 2. 获取日期
        LocalDate now = LocalDate.now();

        // 3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;

        // 4. 获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5. 获取本月截止到今天的签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                org.springframework.data.redis.connection.BitFieldSubCommands.create()
                        .get(org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));

        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }

        // 6. 循环遍历，统计连续签到天数
        int count = 0;
        while (true) {
            if ((num & 1) == 0) {
                // 未签到
                break;
            } else {
                // 已签到
                count++;
            }
            num >>>= 1;
        }

        return Result.ok(count);
    }

    @Override
    public Result signDays() {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();

        // 2. 获取日期
        LocalDate now = LocalDate.now();

        // 3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;

        // 4. 获取本月天数
        int dayOfMonth = now.getDayOfMonth();

        // 5. 获取本月所有签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                org.springframework.data.redis.connection.BitFieldSubCommands.create()
                        .get(org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));

        if (result == null || result.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }

        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(new ArrayList<>());
        }

        // 6. 解析签到日期列表
        List<Integer> days = new ArrayList<>();
        int day = 1;
        while (num > 0) {
            if ((num & 1) == 1) {
                days.add(day);
            }
            day++;
            num >>>= 1;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("days", days);
        data.put("total", days.size());
        return Result.ok(data);
    }
}
