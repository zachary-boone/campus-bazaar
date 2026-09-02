package com.campus.bazaar.mapper;

import com.campus.bazaar.entity.Goods;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 *  Mapper 接口
 * </p>
 */
public interface GoodsMapper extends BaseMapper<Goods> {

    /**
     * 下单预占商品（原子 CAS：仅在售 1 → 交易中 4），防止同一商品被多人同时下单（防超卖）。
     * @return 影响行数，1 表示预占成功，0 表示已被他人下单/已下架/已售
     */
    @Update("UPDATE tb_goods SET status = 4, update_time = NOW() WHERE id = #{id} AND status = 1")
    int preemptForOrder(@Param("id") Long id);

    /**
     * 支付成功置为已售（原子：仅交易中 4 → 已售 2），销量 +1。
     * @return 影响行数
     */
    @Update("UPDATE tb_goods SET status = 2, sold = sold + 1, update_time = NOW() WHERE id = #{id} AND status = 4")
    int markSold(@Param("id") Long id);

    /**
     * 释放商品预占（订单取消/超时关单：交易中 4 → 在售 1），让商品重新可售。
     * @return 影响行数
     */
    @Update("UPDATE tb_goods SET status = 1, update_time = NOW() WHERE id = #{id} AND status = 4")
    int releasePreempt(@Param("id") Long id);
}
