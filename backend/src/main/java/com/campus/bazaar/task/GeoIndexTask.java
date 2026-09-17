package com.campus.bazaar.task;

import com.campus.bazaar.service.IGoodsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Redis GEO 索引自愈任务
 * <p>
 * 背景：GEO 坐标只在"经接口发布/编辑商品"时写入，用 SQL 直接导入的商品
 * （种子数据、升级脚本）不会进索引，于是「附近商品」「地图」永远查不到东西。
 * <p>
 * 处理方式：启动后先自愈一次，之后定期检查——只在"索引基数 ≠ DB 在售数"时才全量重建，
 * 平时就是一次 ZCARD 的开销，几乎无成本。
 */
@Slf4j
@Component
public class GeoIndexTask {

    @Resource
    private IGoodsService goodsService;

    /** 启动 5s 后自愈一次，之后每 10 分钟检查一次 */
    @Scheduled(initialDelay = 5_000, fixedDelay = 600_000)
    public void syncGeoIndex() {
        try {
            int rebuilt = goodsService.syncGeoIndex();
            if (rebuilt > 0) {
                log.info("[GEO] 索引自愈完成，写入 {} 件在售商品坐标", rebuilt);
            }
        } catch (Exception e) {
            log.warn("[GEO] 索引自愈失败: {}", e.getMessage());
        }
    }
}
