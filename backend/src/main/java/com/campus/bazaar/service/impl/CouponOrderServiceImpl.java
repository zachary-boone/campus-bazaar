package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.CouponOrder;
import com.campus.bazaar.entity.SeckillCoupon;
import com.campus.bazaar.mapper.CouponOrderMapper;
import com.campus.bazaar.mapper.SeckillCouponMapper;
import com.campus.bazaar.service.ICouponOrderService;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class CouponOrderServiceImpl extends ServiceImpl<CouponOrderMapper, CouponOrder> implements ICouponOrderService {

    @Resource
    private SeckillCouponMapper seckillCouponMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public Result seckillCoupon(Long couponId) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();

        // 2. 查询秒杀优惠券
        SeckillCoupon seckillCoupon = seckillCouponMapper.selectById(couponId);
        if (seckillCoupon == null) {
            return Result.fail("优惠券不存在");
        }

        // 3. 判断是否在秒杀时间范围内
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(seckillCoupon.getBeginTime())) {
            return Result.fail("秒杀尚未开始");
        }
        if (now.isAfter(seckillCoupon.getEndTime())) {
            return Result.fail("秒杀已经结束");
        }

        // 4. 判断库存
        if (seckillCoupon.getStock() < 1) {
            return Result.fail("库存不足");
        }

        // 5. 判断用户是否已经抢购过（一人一单）
        Integer count = query()
                .eq("user_id", userId)
                .eq("coupon_id", couponId)
                .count();
        if (count > 0) {
            return Result.fail("每人限购一张");
        }

        // 6. 扣减库存
        int update = seckillCouponMapper.update(null, new QueryWrapper<SeckillCoupon>()
                .eq("coupon_id", couponId)
                .gt("stock", 0)
                .setSql("stock = stock - 1"));
        if (update == 0) {
            return Result.fail("库存不足");
        }

        // 7. 创建订单
        CouponOrder order = new CouponOrder();
        order.setId(System.currentTimeMillis());
        order.setUserId(userId);
        order.setCouponId(couponId);
        order.setStatus(1); // 未支付
        order.setCreateTime(LocalDateTime.now());
        save(order);

        return Result.ok(order.getId());
    }
}
