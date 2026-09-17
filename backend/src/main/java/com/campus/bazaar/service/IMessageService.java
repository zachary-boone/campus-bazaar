package com.campus.bazaar.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Message;

/**
 * 站内消息服务
 */
public interface IMessageService extends IService<Message> {

    /**
     * 我的消息（分页，可按类型过滤，按时间倒序）
     * @param current 页码（从 1 开始）
     * @param type    0=全部 1系统 2交易 3互动
     */
    Result myMessages(Integer current, Integer type);

    /**
     * 当前用户未读消息数（底栏红点、页面顶部角标共用）
     */
    Result unreadCount();

    /**
     * 标记单条已读（校验归属，只能读自己的消息）
     */
    Result readOne(Long id);

    /**
     * 全部标记已读（幂等：没有未读时也返回成功）
     */
    Result readAll();
}
