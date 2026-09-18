package com.campus.bazaar.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * 卖家私信服务
 */
public interface IChatService extends IService<ChatMessage> {

    /**
     * 发送私信：写对话明细 + 给收件人同步一条 tb_message(type=4) 通知
     * @param receiverId 收件人（卖家）
     * @param goodsId    关联商品（可空）
     * @param content    留言内容（1~200 字）
     * @return ok
     */
    Result send(Long receiverId, Long goodsId, String content);

    /**
     * 与某人的完整对话（双向，按时间升序），并把对方发来的未读标为已读
     * @param peerId 对方用户id
     * @return { peer: 用户卡片, messages: [ { mine, content, createTime } ] }
     */
    Result conversation(Long peerId);

    /**
     * 对话消息列表（供 conversation 组装）
     */
    List<Map<String, Object>> dialogList(Long meId, Long peerId);
}
