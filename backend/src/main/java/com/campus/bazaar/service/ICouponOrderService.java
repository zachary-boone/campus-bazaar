package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.CouponOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ICouponOrderService extends IService<CouponOrder> {

    /**
     * 秒杀优惠券
     * @param couponId 优惠券id
     * @return 订单id
     */
    Result seckillCoupon(Long couponId);
}
