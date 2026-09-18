package com.campus.bazaar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 卖家私信对话（双向消息真相）。
 * 收件人视角的 is_read；发送时同步写一条 tb_message(type=4) 通知收件人。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 发件人用户id
     */
    private Long senderId;

    /**
     * 收件人用户id
     */
    private Long receiverId;

    /**
     * 关联商品id（可空）
     */
    private Long goodsId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 收件人是否已读：0未读 1已读
     */
    private Integer isRead;

    /**
     * 发送时间
     */
    private LocalDateTime createTime;
}
