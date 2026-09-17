package com.campus.bazaar.controller;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.service.ISignService;
import com.campus.bazaar.utils.Idempotent;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 每日签到 / 签到运费券
 */
@RestController
@RequestMapping("/sign")
public class SignController {

    @Resource
    private ISignService signService;

    /**
     * 用户签到（防连点重复提交）
     * @return 签到结果（连续天数 / 是否获得运费券资格 / 可领取张数）
     */
    @PostMapping
    @Idempotent(ttl = 3, message = "签到处理中，请勿重复提交")
    public Result sign() {
        return signService.sign();
    }

    /**
     * 查询连续签到天数
     * @return 连续签到天数
     */
    @GetMapping("/count")
    public Result signCount() {
        return signService.signCount();
    }

    /**
     * 查询签到记录（签到日历）
     * @param year  年，可不传（默认当前年）
     * @param month 月，可不传（默认当前月）
     * @return 该月签到记录
     */
    @GetMapping("/records")
    public Result signRecords(@RequestParam(value = "year", required = false) Integer year,
                              @RequestParam(value = "month", required = false) Integer month) {
        return signService.signRecords(year, month);
    }

    /**
     * 我领取过的签到运费券
     * @return 券列表
     */
    @GetMapping("/coupons")
    public Result signCoupons() {
        return signService.signCoupons();
    }

    /**
     * 领取连续签到满一周的运费券（防连点重复提交）
     * @return 券信息
     */
    @PostMapping("/coupon")
    @Idempotent(ttl = 2, message = "领取处理中，请勿重复提交")
    public Result claimWeekCoupon() {
        return signService.claimWeekCoupon();
    }
}
