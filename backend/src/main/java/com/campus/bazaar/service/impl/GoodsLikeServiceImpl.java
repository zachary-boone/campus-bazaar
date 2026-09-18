package com.campus.bazaar.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.entity.GoodsLike;
import com.campus.bazaar.mapper.GoodsLikeMapper;
import com.campus.bazaar.mapper.GoodsMapper;
import com.campus.bazaar.service.IGoodsLikeService;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 商品"想要"（求购）服务
 * <p>
 * 一致性设计：tb_goods_like 明细是真相，tb_goods.wants 是同事务维护的计数列
 * （求购榜按 wants 排序，SQL 直接 ORDER BY，不必对明细做聚合）。
 * 两处防线保证不漂移：
 * <ul>
 *   <li>计数增减只在"明细真正插入/删除"时执行（受影响行数 = 1 才 +1/-1，
 *       幂等重试不会重复计数）；</li>
 *   <li>upgrade-0918-wants.sql 提供从明细回填的修正语句，可随时校准。</li>
 * </ul>
 */
@Slf4j
@Service
public class GoodsLikeServiceImpl extends ServiceImpl<GoodsLikeMapper, GoodsLike> implements IGoodsLikeService {

    @Resource
    private GoodsMapper goodsMapper;

    @Override
    @Transactional
    public Result like(Long goodsId, Boolean isLike) {
        Long userId = UserHolder.getUserId();
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            return Result.fail("商品不存在");
        }
        if (userId.equals(goods.getSellerId())) {
            return Result.fail("不能想要自己发布的商品");
        }

        boolean changed;
        if (Boolean.TRUE.equals(isLike)) {
            try {
                changed = save(new GoodsLike()
                        .setGoodsId(goodsId).setUserId(userId).setCreateTime(LocalDateTime.now()));
            } catch (DuplicateKeyException e) {
                changed = false;    // 重复"想要"：幂等成功，不重复计数
            }
        } else {
            changed = remove(new QueryWrapper<GoodsLike>()
                    .eq("goods_id", goodsId).eq("user_id", userId));
        }

        if (changed) {
            // 只有明细真的增删了才动计数，幂等重试不会把 wants 推歪
            goodsMapper.update(null, new UpdateWrapper<Goods>()
                    .eq("id", goodsId)
                    .setSql(isLike ? "wants = wants + 1" : "wants = wants - 1"));
        }

        long wants = count(new QueryWrapper<GoodsLike>().eq("goods_id", goodsId));
        return Result.ok((int) wants);
    }

    @Override
    public Map<String, Object> status(Long goodsId) {
        Long userId = UserHolder.getUserId();   // 公开接口：未登录为 null
        Map<String, Object> map = new HashMap<>();
        map.put("wants", (int) count(new QueryWrapper<GoodsLike>().eq("goods_id", goodsId)));
        map.put("liked", userId != null && count(new QueryWrapper<GoodsLike>()
                .eq("goods_id", goodsId).eq("user_id", userId)) > 0);
        return map;
    }
}
