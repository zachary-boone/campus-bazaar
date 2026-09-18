package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.ChatMessage;
import com.campus.bazaar.entity.Message;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.mapper.ChatMessageMapper;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.service.IChatService;
import com.campus.bazaar.service.IMessageService;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 卖家私信服务
 * <p>
 * 双写一致性：{@code tb_chat_message} 是对话真相（双向完整记录）；
 * 发送时在同一事务内给收件人写一条 {@code tb_message(type=4)} 站内通知
 * （带 sender_id 与 /chat.html 跳转链接），收件人在"我的消息"看到未读红点、
 * 点击直达对话页。对话页查询按双向拉取并顺带把收到的未读标为已读。
 */
@Slf4j
@Service
public class ChatServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatService {

    /** 私信在站内消息里的类型 */
    public static final int MSG_TYPE_CHAT = 4;

    /** 单条留言长度上限 */
    private static final int MAX_LEN = 200;

    @Resource
    private UserMapper userMapper;

    @Resource
    private IMessageService messageService;

    @Override
    @Transactional
    public Result send(Long receiverId, Long goodsId, String content) {
        Long meId = UserHolder.getUserId();
        if (receiverId == null) {
            return Result.fail("缺少收件人");
        }
        if (receiverId.equals(meId)) {
            return Result.fail("不能给自己发私信");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            return Result.fail("留言内容不能为空");
        }
        if (text.length() > MAX_LEN) {
            return Result.fail("留言最多 " + MAX_LEN + " 字");
        }
        User receiver = userMapper.selectById(receiverId);
        if (receiver == null) {
            return Result.fail("对方不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        save(new ChatMessage()
                .setSenderId(meId).setReceiverId(receiverId)
                .setGoodsId(goodsId).setContent(text)
                .setIsRead(0).setCreateTime(now));

        // 站内消息通知：收件人"我的消息"出现未读私信，点击直达对话页
        String preview = text.length() > 50 ? text.substring(0, 50) + "…" : text;
        Message notice = new Message()
                .setUserId(receiverId).setSenderId(meId)
                .setType(MSG_TYPE_CHAT)
                .setTitle("收到新私信")
                .setContent(preview)
                .setLink("/chat.html?userId=" + meId + (goodsId != null ? "&goodsId=" + goodsId : ""))
                .setIsRead(0).setCreateTime(now);
        messageService.save(notice);

        log.info("[Chat] 用户{} → 用户{} 私信已发送（goods={}）", meId, receiverId, goodsId);
        return Result.ok();
    }

    @Override
    public Result conversation(Long peerId) {
        Long meId = UserHolder.getUserId();
        User peer = userMapper.selectById(peerId);
        if (peer == null) {
            return Result.fail("对方不存在");
        }

        // 进入对话即把对方发来的未读标为已读
        update(new UpdateWrapper<ChatMessage>()
                .eq("sender_id", peerId).eq("receiver_id", meId).eq("is_read", 0)
                .set("is_read", 1));

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> peerCard = new HashMap<>();
        peerCard.put("id", peer.getId());
        peerCard.put("nickName", peer.getNickName());
        peerCard.put("icon", peer.getIcon());
        data.put("peer", peerCard);
        data.put("messages", dialogList(meId, peerId));
        return Result.ok(data);
    }

    @Override
    public List<Map<String, Object>> dialogList(Long meId, Long peerId) {
        // 双向拉取：我发给 TA 的 + TA 发给我的，合并后按时间升序拼成对话。
        // （不用 and().or().and() 嵌套——两次单条件查询合并，语义直白不易错）
        List<ChatMessage> both = new ArrayList<>(query()
                .eq("sender_id", meId).eq("receiver_id", peerId).list());
        both.addAll(query()
                .eq("sender_id", peerId).eq("receiver_id", meId).list());
        both.sort(Comparator.comparing(ChatMessage::getCreateTime));
        return both.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("mine", meId.equals(m.getSenderId()));
            item.put("content", m.getContent());
            item.put("createTime", m.getCreateTime());
            return item;
        }).collect(Collectors.toList());
    }
}
