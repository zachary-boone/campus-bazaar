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
 * <p>
 * 
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_coupon_order")
public class CouponOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 下单的用户id
     */
    private Long userId;

    /**
     * 关联的主订单id（预留，秒杀券订单与商品订单打通时使用）
     */
    private Long orderId;

    /**
     * 购买的代金券id
     */
    private Long couponId;

    /**
     * 支付方式 0：未支付；1：余额支付；2：支付宝；3：微信
     */
    private Integer payType;

    /**
     * 订单状态（与表定义一致）：0：未支付；1：已支付；2：已核销；-1：超时关闭
     */
    private Integer status;

    /**
     * 下单时间
     */
    private LocalDateTime createTime;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 核销时间
     */
    private LocalDateTime useTime;

    /**
     * 退款时间
     */
    private LocalDateTime refundTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
