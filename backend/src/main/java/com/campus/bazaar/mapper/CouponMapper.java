package com.campus.bazaar.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.bazaar.entity.Coupon;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    List<Coupon> queryCouponOfGoods(@Param("goodsId") Long goodsId);
}
