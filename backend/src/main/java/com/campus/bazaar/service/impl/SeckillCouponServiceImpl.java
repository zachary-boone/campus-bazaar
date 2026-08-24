package com.campus.bazaar.service.impl;

import com.campus.bazaar.entity.SeckillCoupon;
import com.campus.bazaar.mapper.SeckillCouponMapper;
import com.campus.bazaar.service.ISeckillCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 */
@Service
public class SeckillCouponServiceImpl extends ServiceImpl<SeckillCouponMapper, SeckillCoupon> implements ISeckillCouponService {

}
