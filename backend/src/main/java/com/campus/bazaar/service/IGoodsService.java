package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IGoodsService extends IService<Goods> {

    /**
     * 根据id查询商品详情
     * @param id 商品id
     * @return 商品详情
     */
    Result queryById(Long id);

    /**
     * 发布商品
     * @param goods 商品信息
     * @return 商品id
     */
    Result publishGoods(Goods goods);

    /**
     * 编辑商品
     * @param goods 商品信息
     * @return 操作结果
     */
    Result updateGoods(Goods goods);

    /**
     * 下架商品
     * @param goodsId 商品id
     * @return 操作结果
     */
    Result offShelfGoods(Long goodsId);

    /**
     * 上架商品
     * @param goodsId 商品id
     * @return 操作结果
     */
    Result onShelfGoods(Long goodsId);

    /**
     * 查询我发布的商品
     * @param current 页码
     * @return 商品列表
     */
    Result queryMyGoods(Integer current);

    /**
     * 删除商品
     * @param goodsId 商品id
     * @return 操作结果
     */
    Result deleteGoods(Long goodsId);
}
