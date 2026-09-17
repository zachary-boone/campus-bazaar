package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;

public interface ISignService {

    /**
     * 用户签到（Redis Bitmap 打点 + tb_sign 落库兜底）
     * @return 连续签到天数、是否触发运费券资格、可领取张数
     */
    Result sign();

    /**
     * 查询当前连续签到天数
     * @return 连续签到天数
     */
    Result signCount();

    /**
     * 查询签到记录（按月，用于签到日历）
     * @param year  年，为空取当前年
     * @param month 月(1-12)，为空取当前月
     * @return 该月已签日期列表 + 连续天数 + 可领取运费券张数
     */
    Result signRecords(Integer year, Integer month);

    /**
     * 查询我领取过的签到运费券
     * @return 券列表
     */
    Result signCoupons();

    /**
     * 领取"连续签到满一周"获得的运费券
     * @return 券信息与剩余可领取张数
     */
    Result claimWeekCoupon();
}
