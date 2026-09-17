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
     * 查询某卖家正在出售的商品（他人主页"TA 的在售商品"）
     * @param sellerId 卖家用户id
     * @param current  页码
     * @return 在售商品列表（只含 status=1）
     */
    Result queryGoodsOfSeller(Long sellerId, Integer current);

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

    /**
     * 地图页数据（Redis GEO）：中心点 + 半径内商品（含距离）+ 各校区在售数量
     * @param x      我的经度，可空（空则用校园中心，取在售商品坐标中位数）
     * @param y      我的纬度，可空
     * @param radius 半径（公里），默认 3
     * @param area   校区过滤，可空
     * @return { center, located, radius, count, areas, goods }
     */
    Result queryMapGoods(Double x, Double y, Double radius, String area);

    /**
     * 全量重建 Redis GEO 索引（以 DB 在售商品为准）
     * <p>
     * SQL 直接导入的商品不会进 GEO 索引，导致「附近/地图」查不到数据；
     * 启动时自愈一次，也可手动触发。
     * @return 写入索引的商品数
     */
    int rebuildGeoIndex();

    /**
     * 增量自愈 GEO 索引：索引与 DB 一致时直接跳过，不一致才全量重建
     * @return 重建写入的商品数；0 表示本来就是一致的
     */
    int syncGeoIndex();
}
