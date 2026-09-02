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
            globalRate = 200, globalCapacity = 500,
            message = "秒杀请求过于频繁，请稍后再试")
    public Result seckillVoucher(@PathVariable("id") Long couponId) {
        return couponOrderService.seckillCoupon(couponId);
    }

    /**
     * 秒杀券订单支付（模拟，状态 0→1；防重复提交）
     * @param id 券订单id
     */
    @PostMapping("pay/{id}")
    @com.campus.bazaar.utils.Idempotent(ttl = 3, message = "支付处理中，请勿重复提交")
    public Result payCouponOrder(@PathVariable("id") Long id) {
        return couponOrderService.payCouponOrder(id);
    }

    /**
     * 秒杀券订单核销（状态 1→2，模拟券使用）
     * @param id 券订单id
     */
    @PutMapping("verify/{id}")
    public Result verifyCouponOrder(@PathVariable("id") Long id) {
        return couponOrderService.verifyCouponOrder(id);
    }
}
