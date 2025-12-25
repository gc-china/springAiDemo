package org.zerolg.aidemo2.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis DLQ (死信队列) 监控指标
 * 暴露 DLQ 的堆积数量到 Actuator，以便在 Grafana 中展示趋势
 */
@Component
@RequiredArgsConstructor
public class RedisDlqMetrics implements MeterBinder {

    // DLQ Key (需与 DlqMonitorTask 中保持一致，建议提取到 RedisKeys 常量类)
    // 这里暂时硬编码，实际建议使用 RedisKeys.SESSION_DLQ
    private static final String DLQ_KEY = "aidemo:session:dlq";
    private final StringRedisTemplate redisTemplate;

    @Override
    public void bindTo(MeterRegistry registry) {
        // 定义一个 Gauge，动态获取 List 长度
        Gauge.builder("aidemo.redis.dlq.size", this, RedisDlqMetrics::getDlqSize)
                .description("Redis 死信队列(DLQ) 当前积压数量")
                .tag("queue", DLQ_KEY)
                .register(registry);
    }

    /**
     * 获取 DLQ 长度
     * LLEN 命令是 O(1) 的，非常快，高频调用也没问题
     */
    private long getDlqSize() {
        try {
            Long size = redisTemplate.opsForList().size(DLQ_KEY);
            return size != null ? size : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}