package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface ICouponService extends IService<Coupon> {

    Result queryCouponOfGoods(Long goodsId);

    void addSeckillCoupon(Coupon coupon);
}
