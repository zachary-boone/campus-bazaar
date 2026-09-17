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
 * 站内消息（底栏「消息」）
 * <p>
 * 类型 type：1系统 2交易 3互动（前端 tab 与图标都按它区分）。
 * {@code link} 为可空的可跳转链接（商品详情/帖子详情/签到页等），
 * 点击消息时前端先标已读再跳转。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_message")
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 收件人用户id */
    private Long userId;

    /** 类型：1系统 2交易 3互动 */
    private Integer type;

    /** 标题（列表一行显示） */
    private String title;

    /** 正文摘要 */
    private String content;

    /** 点击跳转链接（可空） */
    private String link;

    /** 0未读 1已读 */
    private Integer isRead;

    /** 创建时间 */
    private LocalDateTime createTime;
}
