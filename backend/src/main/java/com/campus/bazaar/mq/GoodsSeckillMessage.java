package com.campus.bazaar.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 商品秒杀异步下单消息体
 * <p>
 * 商品秒杀接口 Redis 预扣库存成功后发送，消费者据此异步创建 tb_order 订单。
 * 与券秒杀的 {@link SeckillMessage} 结构一致，只是作用对象由券改为商品。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsSeckillMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 秒杀商品 id */
    private Long goodsId;

    /** 下单用户 id */
    private Long userId;

    /** 消息唯一 ID（消费幂等用，生产者生成 UUID） */
    private String msgId;
}
