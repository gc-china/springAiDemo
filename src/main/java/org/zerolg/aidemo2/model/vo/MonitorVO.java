package org.zerolg.aidemo2.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 系统监控看板数据 VO
 */
@Data
@Builder
public class MonitorVO {

    // --- 核心健康指标 ---

    /**
     * 死信队列积压数量 (Critical)
     */
    private Double dlqSize;

    /**
     * Stream 消费积压量 (Warning)
     */
    private Double streamLag;

    // --- 业务统计指标 ---

    private Double archiveSuccessCount;
    private Double archiveErrorCount;

    // --- 性能指标 ---

    /**
     * Redis 写入 P99 延迟 (毫秒)
     */
    private Double redisP99Latency;
}