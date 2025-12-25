package org.zerolg.aidemo2.controller;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zerolg.aidemo2.model.vo.MonitorVO;

import java.util.concurrent.TimeUnit;

/**
 * 监控看板聚合接口
 * 供前端管理后台轮询使用
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MeterRegistry meterRegistry;

    @GetMapping("/dashboard")
    public MonitorVO getDashboardData() {
        return MonitorVO.builder()
                .dlqSize(getGaugeValue("aidemo.redis.dlq.size"))
                .streamLag(getGaugeValue("aidemo.redis.stream.lag"))
                .archiveSuccessCount(getCounterValue("aidemo.session.archive.success"))
                .archiveErrorCount(getCounterValue("aidemo.session.archive.error"))
                .redisP99Latency(getTimerP99("aidemo.redis.write.latency"))
                .build();
    }

    // --- 辅助方法：安全获取指标，防止指标未初始化导致报错 ---

    private double getGaugeValue(String metricName) {
        Gauge gauge = meterRegistry.find(metricName).gauge();
        return gauge != null ? gauge.value() : 0.0;
    }

    private double getCounterValue(String metricName) {
        var counter = meterRegistry.find(metricName).counter();
        return counter != null ? counter.count() : 0.0;
    }

    /**
     * 获取 Timer 的 P99 值
     * 需要在 Config 中开启 .percentiles(0.99) 才能获取到
     */
    private double getTimerP99(String metricName) {
        Timer timer = meterRegistry.find(metricName).timer();
        if (timer == null) {
            return 0.0;
        }
        // 获取快照中的百分位数据
        ValueAtPercentile[] percentiles = timer.takeSnapshot().percentileValues();
        for (ValueAtPercentile v : percentiles) {
            if (v.percentile() == 0.99) {
                // 转换为毫秒
                return v.value(TimeUnit.MILLISECONDS);
            }
        }
        return 0.0;
    }
}