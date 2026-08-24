package com.campus.bazaar.controller;


import com.campus.bazaar.dto.Result;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/coupon-order")
public class CouponOrderController {
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long couponId) {
        return Result.fail("功能未完成");
    }
}
