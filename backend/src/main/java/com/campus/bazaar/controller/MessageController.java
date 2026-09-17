package com.campus.bazaar.controller;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.service.IMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 站内消息（底栏「消息」）
 * <p>
 * 全部接口都需要登录（不在 WebMvcConfig 白名单里），
 * 未登录由 LoginInterceptor 统一返回 401，前端 common.js 会跳登录页。
 */
@RestController
@RequestMapping("/messages")
public class MessageController {

    @Resource
    private IMessageService messageService;

    /**
     * 我的消息（分页）
     * @param current 页码
     * @param type    0=全部 1系统 2交易 3互动
     */
    @GetMapping("/of/me")
    public Result ofMe(@RequestParam(value = "current", defaultValue = "1") Integer current,
                       @RequestParam(value = "type", defaultValue = "0") Integer type) {
        return messageService.myMessages(current, type);
    }

    /**
     * 未读消息数（底栏红点 + 页面顶部）
     */
    @GetMapping("/unread/count")
    public Result unreadCount() {
        return messageService.unreadCount();
    }

    /**
     * 标记单条已读（幂等）
     */
    @PutMapping("/read/{id}")
    public Result readOne(@PathVariable("id") Long id) {
        return messageService.readOne(id);
    }

    /**
     * 全部标记已读（幂等）
     */
    @PutMapping("/read/all")
    public Result readAll() {
        return messageService.readAll();
    }
}
