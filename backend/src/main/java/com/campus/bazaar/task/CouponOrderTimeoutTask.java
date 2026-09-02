package com.campus.bazaar.task;

import com.campus.bazaar.service.ICouponOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 秒杀券订单超时关单任务
 * <p>
 * 秒杀下单后初始状态为 0（未支付），若用户在阈值时间内未支付，定时任务将其置为 -1（超时关闭），
 * 并回补 DB/Redis 库存、解除用户秒杀记录（允许重新抢），防止"抢了不付"把库存占死。
 * 默认阈值 15 分钟，每 5 分钟扫描一次。
 */
@Slf4j
@Component
public class CouponOrderTimeoutTask {

    /** 超时阈值（分钟）：超过该时间未支付视为放弃 */
    private static final int TIMEOUT_MINUTES = 15;

    @Resource
    private ICouponOrderService couponOrderService;

    /** 启动 60s 后执行一次，之后每 5 分钟扫描 */
    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void closeExpiredCouponOrders() {
        try {
            int closed = couponOrderService.cancelExpiredCouponOrders(TIMEOUT_MINUTES);
            if (closed > 0) {
                log.warn("[CouponOrderTask] 超时关单完成，共关闭 {} 个未支付券订单并回补库存", closed);
            }
        } catch (Exception e) {
            log.error("[CouponOrderTask] 超时关单扫描异常: {}", e.getMessage(), e);
        }
    }
}
