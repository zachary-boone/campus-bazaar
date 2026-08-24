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
}
