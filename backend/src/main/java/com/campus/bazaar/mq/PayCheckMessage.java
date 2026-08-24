package com.campus.bazaar.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 支付状态轮询消息体
 * <p>
 * 携带订单号与当前重试级别（1~5），
 * 消费者检查订单是否已支付：未支付且未到最大级别 → 投递到下一级延迟队列（TTL 翻倍）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayCheckMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;

    /** 当前重试级别：1 级延迟 5s，2 级 10s，…… 5 级 80s，超过 5 级关单 */
    private int retryLevel;
}
