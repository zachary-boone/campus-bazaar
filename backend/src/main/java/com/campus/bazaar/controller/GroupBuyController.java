package com.campus.bazaar.controller;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.service.IGroupBuyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 拼单购买（GroupBuy）
 * <p>
 * 规则：拼单价 9 折 / 2 人成团 / 24h 有效。
 * 查询类接口公开；开团/参团/我的拼单需登录（不在 WebMvcConfig 白名单）。
 */
@RestController
@RequestMapping("/group")
public class GroupBuyController {

    @Resource
    private IGroupBuyService groupBuyService;

    /**
     * 拼单大厅：进行中的拼单（带商品卡片、成团进度、剩余时间）
     */
    @GetMapping("/active")
    public Result activeGroups() {
        return groupBuyService.activeGroups();
    }

    /**
     * 某商品的进行中拼单（商品详情页"参与拼单"入口用）
     */
    @GetMapping("/goods/{goodsId}")
    public Result activeByGoods(@PathVariable("goodsId") Long goodsId) {
        return groupBuyService.activeByGoods(goodsId);
    }

    /**
     * 开团（需登录）：当前用户成为团长兼第 1 个成员
     */
    @PostMapping("/create/{goodsId}")
    public Result create(@PathVariable("goodsId") Long goodsId) {
        return groupBuyService.createGroup(goodsId);
    }

    /**
     * 参团（需登录）：Redisson 锁防超员，人满自动成团
     */
    @PostMapping("/join/{groupId}")
    public Result join(@PathVariable("groupId") Long groupId) {
        return groupBuyService.joinGroup(groupId);
    }

    /**
     * 我的拼单（需登录）：我发起的 + 我参加的
     */
    @GetMapping("/of/me")
    public Result myGroups() {
        return groupBuyService.myGroups();
    }
}
