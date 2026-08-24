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
     * 秒杀优惠券
     * @param couponId 优惠券id
     * @return 订单id
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long couponId) {
        return couponOrderService.seckillCoupon(couponId);
    }
}
