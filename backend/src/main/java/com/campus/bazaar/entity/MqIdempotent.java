package com.campus.bazaar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MQ 消费幂等表：以 msg_id 唯一键保证消息只被处理一次
 */
@Data
@TableName("tb_mq_idempotent")
public class MqIdempotent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 消息唯一 ID（生产者生成 UUID） */
    private String msgId;

    /** 业务类型：seckill_order / pay_check 等 */
    private String businessType;

    private LocalDateTime createTime;
}
