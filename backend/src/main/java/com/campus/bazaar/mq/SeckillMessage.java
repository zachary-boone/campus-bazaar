package com.campus.bazaar.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀异步下单消息体
 * <p>
 * 秒杀接口 Redis 预扣成功后发送，消费者据此异步创建订单。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 秒杀优惠券 id */
    private Long couponId;

    /** 下单用户 id */
    private Long userId;
}
