package com.campus.bazaar.controller;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.service.ICouponOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/coupon-order")
public class CouponOrderController {

    @Resource
    private ICouponOrderService couponOrderService;

    /**
     * 秒杀优惠券（Redis 预扣 + 异步下单；令牌桶限流保护）
     * @param couponId 优惠券id
     * @return 0 表示已受理，订单异步创建
     */
    @PostMapping("seckill/{id}")
    @com.campus.bazaar.utils.RateLimit(key = "seckill", rate = 20, capacity = 50,
            message = "秒杀请求过于频繁，请稍后再试")
    public Result seckillVoucher(@PathVariable("id") Long couponId) {
        return couponOrderService.seckillCoupon(couponId);
    }
}
