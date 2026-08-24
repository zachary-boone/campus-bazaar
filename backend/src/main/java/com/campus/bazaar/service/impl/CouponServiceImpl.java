package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Coupon;
import com.campus.bazaar.mapper.CouponMapper;
import com.campus.bazaar.entity.SeckillCoupon;
import com.campus.bazaar.service.ISeckillCouponService;
import com.campus.bazaar.service.ICouponService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    @Resource
    private ISeckillCouponService seckillCouponService;

    @Override
    public Result queryCouponOfGoods(Long goodsId) {
        // 查询优惠券信息
        List<Coupon> vouchers = getBaseMapper().queryCouponOfGoods(goodsId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillCoupon(Coupon coupon) {
        // 保存优惠券
        save(coupon);
        // 保存秒杀信息
        SeckillCoupon seckillVoucher = new SeckillCoupon();
        seckillVoucher.setCouponId(coupon.getId());
        seckillVoucher.setStock(coupon.getStock());
        seckillVoucher.setBeginTime(coupon.getBeginTime());
        seckillVoucher.setEndTime(coupon.getEndTime());
        seckillCouponService.save(seckillVoucher);
    }
}
