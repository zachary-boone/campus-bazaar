package com.campus.bazaar.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.GoodsLike;

import java.util.Map;

/**
 * 商品"想要"（求购）服务
 */
public interface IGoodsLikeService extends IService<GoodsLike> {

    /**
     * 想要/取消想要（幂等，事务内同步维护 tb_goods.wants 计数）
     * @param goodsId 商品id
     * @param isLike  true=想要 false=取消
     * @return 操作后的最新想要人数
     */
    Result like(Long goodsId, Boolean isLike);

    /**
     * 查询某商品的想要状态与人数（公开：未登录 liked=false）
     * @return { liked: Boolean, wants: Integer }
     */
    Map<String, Object> status(Long goodsId);
}
