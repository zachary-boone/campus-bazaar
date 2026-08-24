package com.campus.bazaar.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * 业务指标收集器（Micrometer，暴露到 /actuator/metrics）
 * <p>
 * - seckill.request.total   秒杀请求数
 * - seckill.success.total  秒杀受理成功数
 * - rate.limit.rejected    令牌桶限流拒绝数
 * - idempotent.rejected    防重复提交拒绝数
 */
@Component
public class BizMetrics {

    @Resource
    private MeterRegistry meterRegistry;

    public static Counter seckillRequests;
    public static Counter seckillSuccess;
    public static Counter rateLimitRejected;
    public static Counter idempotentRejected;

    @PostConstruct
    public void init() {
        seckillRequests = Counter.builder("seckill.request.total")
                .description("秒杀请求总数").register(meterRegistry);
        seckillSuccess = Counter.builder("seckill.success.total")
                .description("秒杀受理成功数").register(meterRegistry);
        rateLimitRejected = Counter.builder("rate.limit.rejected")
                .description("令牌桶限流拒绝数").register(meterRegistry);
        idempotentRejected = Counter.builder("idempotent.rejected")
                .description("防重复提交拒绝数").register(meterRegistry);
    }
}
