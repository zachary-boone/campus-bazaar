package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;

public interface ISignService {

    /**
     * 用户签到
     * @return 签到结果
     */
    Result sign();

    /**
     * 查询连续签到天数
     * @return 连续签到天数
     */
    Result signCount();

    /**
     * 查询本月签到记录
     * @return 签到日期列表
     */
    Result signDays();
}
