package com.campus.bazaar.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.entity.SeckillCoupon;
import com.campus.bazaar.mapper.GoodsMapper;
import com.campus.bazaar.mapper.SeckillCouponMapper;
import com.campus.bazaar.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀库存对账任务（券 + 商品）
 * <p>
 * 秒杀链路中库存以 Redis 预扣为"瞬时闸门"、DB 为"最终真相"，
 * 正常情况下通过失败补偿保持一致；但若 MQ 消息丢失、应用宕机等极端场景，
 * Redis 预扣可能未落库。本任务定时以 DB 库存为基准校准 Redis：
 * - Redis key 缺失 → 用 DB 库存初始化（兜底预热）；
 * - Redis 预扣为负 → 修正为 0（防止负数库存泄漏）；
 * - Redis 预扣超出 DB 库存 → 修正为 DB 库存（说明有预扣未落库，回收差额）。
 * 处于 [0, DB库存] 正常区间的值不干预，避免覆盖秒杀进行中的并发预扣。
 */
@Slf4j
@Component
public class StockReconcileTask {

    @Resource
    private SeckillCouponMapper seckillCouponMapper;

    @Resource
    private GoodsMapper goodsMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 启动 30s 后执行一次，之后每 5 分钟对账 */
    @Scheduled(initialDelay = 30_000, fixedDelay = 300_000)
    public void reconcileSeckillStock() {
        reconcileCouponStock();
        reconcileGoodsStock();
    }

    /**
     * 券秒杀库存对账（保留原逻辑）
     */
    private void reconcileCouponStock() {
        List<SeckillCoupon> coupons = seckillCouponMapper.selectList(
                new QueryWrapper<SeckillCoupon>().gt("end_time", LocalDateTime.now()));
        if (coupons.isEmpty()) {
            return;
        }
        int fixed = 0;
        for (SeckillCoupon sc : coupons) {
            String key = RedisConstants.SECKILL_STOCK_KEY + sc.getCouponId();
            long dbStock = sc.getStock() == null ? 0L : sc.getStock().longValue();
            if (reconcile(key, dbStock, "券", sc.getCouponId())) {
                fixed++;
            }
        }
        if (fixed > 0) {
            log.warn("[StockReconcile] 券对账完成，共修正 {} 个秒杀券库存", fixed);
        } else {
            log.info("[StockReconcile] 券对账完成，{} 个秒杀券库存一致", coupons.size());
        }
    }

    /**
     * 商品秒杀库存对账（新增）
     * <p>
     * 只校准"在售 + 配置了秒杀时间窗且未结束"的商品，避免为普通一物一件商品创建无谓的库存 key。
     */
    private void reconcileGoodsStock() {
        List<Goods> goodsList = goodsMapper.selectList(new QueryWrapper<Goods>()
                .eq("status", 1)
                .isNotNull("seckill_begin")
                .and(w -> w.isNull("seckill_end").or().gt("seckill_end", LocalDateTime.now())));
        if (goodsList.isEmpty()) {
            return;
        }
        int fixed = 0;
        for (Goods g : goodsList) {
            String key = RedisConstants.SECKILL_GOODS_STOCK_KEY + g.getId();
            long dbStock = g.getStock() == null ? 0L : g.getStock().longValue();
            // 商品库存为 0（已抢光）时，若 Redis 中有残留的正数则回收到 0
            if (reconcile(key, dbStock, "商品", g.getId())) {
                fixed++;
            }
        }
        if (fixed > 0) {
            log.warn("[StockReconcile] 商品对账完成，共修正 {} 个秒杀商品库存", fixed);
        } else {
            log.info("[StockReconcile] 商品对账完成，{} 个秒杀商品库存一致", goodsList.size());
        }
    }

    /**
     * 单个 key 的对账逻辑（券 / 商品共用）
     * @return true 表示做了修正
     */
    private boolean reconcile(String key, long dbStock, String bizName, Long bizId) {
        String val = stringRedisTemplate.opsForValue().get(key);

        if (val == null) {
            // 1. 缺失 → 用 DB 初始化
            stringRedisTemplate.opsForValue().set(key, String.valueOf(dbStock));
            log.warn("[StockReconcile] {}库存 key 缺失，以 DB 初始化: id={}, stock={}", bizName, bizId, dbStock);
            return true;
        }

        long redisStock;
        try {
            redisStock = Long.parseLong(val);
        } catch (NumberFormatException e) {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(dbStock));
            return true;
        }

        // 2. 负库存 → 修正为 0
        if (redisStock < 0) {
            stringRedisTemplate.opsForValue().set(key, "0");
            log.warn("[StockReconcile] {}库存为负，修正为 0: id={}, redis={}", bizName, bizId, redisStock);
            return true;
        }
        // 3. 预扣超出 DB 库存 → 以 DB 为准回收（预扣未落库）
        if (redisStock > dbStock) {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(dbStock));
            log.warn("[StockReconcile] {}预扣超出 DB，校准为 DB 库存: id={}, redis={}, db={}",
                    bizName, bizId, redisStock, dbStock);
            return true;
        }
        return false;
    }
}
