package com.campus.bazaar.controller;


import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Coupon;
import com.campus.bazaar.service.ICouponService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 */
@RestController
@RequestMapping("/coupon")
public class CouponController {

    @Resource
    private ICouponService couponService;

    /**
     * 新增普通券
     * @param coupon 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    public Result addCoupon(@RequestBody Coupon coupon) {
        couponService.save(coupon);
        return Result.ok(coupon.getId());
    }

    /**
     * 新增秒杀券
     * @param coupon 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    public Result addSeckillCoupon(@RequestBody Coupon coupon) {
        couponService.addSeckillCoupon(coupon);
        return Result.ok(coupon.getId());
    }

    /**
     * 查询店铺的优惠券列表
     * @param goodsId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{goodsId}")
    public Result queryCouponOfGoods(@PathVariable("goodsId") Long goodsId) {
       return couponService.queryCouponOfGoods(goodsId);
    }
}
