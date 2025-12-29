package org.zerolg.aidemo2.audit.service;

import org.zerolg.aidemo2.audit.model.PerformanceMetrics;

import java.time.Duration;
import java.util.Map;

/**
 * 性能监控接口
 */
public interface PerformanceMonitor {

    /**
     * 记录工具执行性能指标
     */
    void recordExecutionMetrics(String toolName, String methodName, PerformanceMetrics metrics);

    /**
     * 获取工具性能统计
     */
    Map<String, Object> getToolPerformanceStats(String toolName);

    /**
     * 获取系统整体性能统计
     */
    Map<String, Object> getSystemPerformanceStats();

    /**
     * 检查性能阈值
     */
    boolean checkPerformanceThresholds(String toolName, PerformanceMetrics metrics);

    /**
     * 获取性能趋势
     */
    Map<String, Object> getPerformanceTrends(String toolName, Duration timeRange);

    /**
     * 生成性能报告
     */
    String generatePerformanceReport(Duration timeRange);
}