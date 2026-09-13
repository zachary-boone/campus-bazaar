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

    /**
     * 商品秒杀（生产端）：Redis Lua 原子预扣库存 + 一人一单校验，成功后发 MQ 异步建单。
     * <p>
     * 返回 0 表示"已受理"（订单由消费者异步创建），并非下单成功。
     * @param goodsId 商品id
     * @return 受理结果
     */
    Result seckillGoods(Long goodsId);

    /**
     * 附近商品检索（Redis GEO 半径查询，按距离升序）
     * @param x      中心经度
     * @param y      中心纬度
     * @param radius 半径（公里）
     * @param area   校区（可空）
     * @return 商品列表（含 distance 距离）
     */
    Result queryNearbyGoods(Double x, Double y, Double radius, String area);
}
