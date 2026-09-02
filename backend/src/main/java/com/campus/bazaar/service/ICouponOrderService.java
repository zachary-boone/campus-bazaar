package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.CouponOrder;
import com.campus.bazaar.mq.SeckillMessage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ICouponOrderService extends IService<CouponOrder> {

    /**
     * 秒杀优惠券（Redis 预扣库存 + 异步下单）
     * @param couponId 优惠券id
     * @return 0 表示已受理，订单异步创建
     */
    Result seckillCoupon(Long couponId);

    /**
     * 异步创建秒杀订单（MQ 消费者调用，事务内二次校验 + 落库）
     */
    void createSeckillOrder(SeckillMessage message);

    /**
     * 秒杀券订单支付（模拟：状态 0→1）
     * @param id 券订单id
     */
    Result payCouponOrder(Long id);

    /**
     * 秒杀券订单核销（状态 1→2，表示券已使用）
     * @param id 券订单id
     */
    Result verifyCouponOrder(Long id);

    /**
     * 超时未支付的券订单关闭并回补库存（定时任务调用）
     * @param minutes 超时阈值（分钟）
     */
    int cancelExpiredCouponOrders(int minutes);
}
