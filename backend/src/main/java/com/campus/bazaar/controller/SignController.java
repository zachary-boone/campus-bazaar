package com.campus.bazaar.controller;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.service.ISignService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/sign")
public class SignController {

    @Resource
    private ISignService signService;

    /**
     * 用户签到
     * @return 签到结果
     */
    @PostMapping
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
     * 查询本月签到记录
     * @return 签到日期列表
     */
    @GetMapping("/days")
    public Result signDays() {
        return signService.signDays();
    }
}
