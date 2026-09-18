package com.campus.bazaar.controller;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.service.IChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 卖家私信（商品详情页"联系卖家"）
 * <p>
 * 全部接口需登录（不在白名单）。
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private IChatService chatService;

    /**
     * 发送私信给卖家
     * body: { receiverId, goodsId(可空), content }
     */
    @PostMapping("/send")
    public Result send(@RequestBody Map<String, Object> body) {
        Long receiverId = body.get("receiverId") == null ? null : Long.valueOf(body.get("receiverId").toString());
        Long goodsId = body.get("goodsId") == null || body.get("goodsId").toString().isEmpty()
                ? null : Long.valueOf(body.get("goodsId").toString());
        String content = body.get("content") == null ? null : body.get("content").toString();
        return chatService.send(receiverId, goodsId, content);
    }

    /**
     * 与某人的对话（双向消息，进入即把对方发来的标为已读）
     */
    @GetMapping("/with/{userId}")
    public Result conversation(@PathVariable("userId") Long userId) {
        return chatService.conversation(userId);
    }
}
