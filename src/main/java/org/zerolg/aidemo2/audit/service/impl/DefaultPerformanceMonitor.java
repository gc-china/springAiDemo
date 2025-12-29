package org.zerolg.aidemo2.audit.service.impl;

import org.zerolg.aidemo2.audit.model.PerformanceMetrics;
import org.zerolg.aidemo2.audit.service.PerformanceMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认性能监控实现
 */
@Service
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = false)
public class DefaultPerformanceMonitor implements PerformanceMonitor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPerformanceMonitor.class);

    // 性能阈值配置
    private static final Duration MAX_EXECUTION_TIME = Duration.ofSeconds(30);
    private static final Duration MAX_PARAMETER_CORRECTION_TIME = Duration.ofSeconds(5);
    private static final int MAX_PARAMETER_TRANSFORMATIONS = 10;

    // 内存存储，生产环境应该使用时序数据库
    private final Map<String, List<PerformanceRecord>> toolMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<PerformanceRecord>> systemMetrics = new ConcurrentHashMap<>();

    @Override
    public void recordExecutionMetrics(String toolName, String methodName, PerformanceMetrics metrics) {
        try {
            PerformanceRecord record = new PerformanceRecord(
                    toolName, methodName, metrics, Instant.now()
            );

            // 按工具存储
            toolMetrics.computeIfAbsent(toolName, k -> new ArrayList<>()).add(record);

            // 系统整体存储
            systemMetrics.computeIfAbsent("all", k -> new ArrayList<>()).add(record);

            // 检查性能阈值
            if (!checkPerformanceThresholds(toolName, metrics)) {
                logger.warn("Performance threshold exceeded for tool: {} method: {}", toolName, methodName);
            }

            logger.debug("Recorded performance metrics for tool: {} method: {}", toolName, methodName);
        } catch (Exception e) {
            logger.error("Failed to record performance metrics for tool: {}", toolName, e);
        }
    }

    @Override
    public Map<String, Object> getToolPerformanceStats(String toolName) {
        List<PerformanceRecord> records = toolMetrics.getOrDefault(toolName, List.of());
        if (records.isEmpty()) {
            return Map.of("toolName", toolName, "recordCount", 0);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("toolName", toolName);
        stats.put("recordCount", records.size());

        // 执行时间统计
        List<Long> executionTimes = records.stream()
                .map(r -> r.metrics.executionTimeMs())
                .collect(Collectors.toList());

        stats.put("averageExecutionTime", calculateAverageMs(executionTimes));
        stats.put("minExecutionTime", executionTimes.stream().min(Long::compareTo).orElse(0L));
        stats.put("maxExecutionTime", executionTimes.stream().max(Long::compareTo).orElse(0L));

        // 参数修正统计
        List<Long> correctionTimes = records.stream()
                .map(r -> r.metrics.parameterCorrectionTimeMs())
                .collect(Collectors.toList());

        stats.put("averageCorrectionTime", calculateAverageMs(correctionTimes));

        // 参数转换统计
        double averageTransformations = records.stream()
                .mapToInt(r -> r.metrics.parameterTransformations())
                .average()
                .orElse(0.0);
        stats.put("averageParameterTransformations", averageTransformations);

        // 缓存命中率
        long cacheHits = records.stream().mapToLong(r -> r.metrics.cacheHit() ? 1 : 0).sum();
        double cacheHitRate = (double) cacheHits / records.size();
        stats.put("cacheHitRate", cacheHitRate);

        // 方法统计
        Map<String, Long> methodCounts = records.stream()
                .collect(Collectors.groupingBy(r -> r.methodName, Collectors.counting()));
        stats.put("methodUsage", methodCounts);

        return stats;
    }

    @Override
    public Map<String, Object> getSystemPerformanceStats() {
        List<PerformanceRecord> allRecords = systemMetrics.getOrDefault("all", List.of());
        if (allRecords.isEmpty()) {
            return Map.of("totalRecords", 0);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", allRecords.size());

        // 按工具分组统计
        Map<String, Long> toolUsage = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.toolName, Collectors.counting()));
        stats.put("toolUsage", toolUsage);

        // 整体性能统计
        List<Long> allExecutionTimes = allRecords.stream()
                .map(r -> r.metrics.executionTimeMs())
                .collect(Collectors.toList());

        stats.put("systemAverageExecutionTime", calculateAverageMs(allExecutionTimes));
        stats.put("systemMinExecutionTime", allExecutionTimes.stream().min(Long::compareTo).orElse(0L));
        stats.put("systemMaxExecutionTime", allExecutionTimes.stream().max(Long::compareTo).orElse(0L));

        // 系统缓存命中率
        long totalCacheHits = allRecords.stream().mapToLong(r -> r.metrics.cacheHit() ? 1 : 0).sum();
        double systemCacheHitRate = (double) totalCacheHits / allRecords.size();
        stats.put("systemCacheHitRate", systemCacheHitRate);

        return stats;
    }

    @Override
    public boolean checkPerformanceThresholds(String toolName, PerformanceMetrics metrics) {
        boolean withinThresholds = true;

        if (metrics.executionTimeMs() > MAX_EXECUTION_TIME.toMillis()) {
            logger.warn("Execution time threshold exceeded for tool: {} - {}ms > {}ms",
                    toolName, metrics.executionTimeMs(), MAX_EXECUTION_TIME.toMillis());
            withinThresholds = false;
        }

        if (metrics.parameterCorrectionTimeMs() > MAX_PARAMETER_CORRECTION_TIME.toMillis()) {
            logger.warn("Parameter correction time threshold exceeded for tool: {} - {}ms > {}ms",
                    toolName, metrics.parameterCorrectionTimeMs(), MAX_PARAMETER_CORRECTION_TIME.toMillis());
            withinThresholds = false;
        }

        if (metrics.parameterTransformations() > MAX_PARAMETER_TRANSFORMATIONS) {
            logger.warn("Parameter transformations threshold exceeded for tool: {} - {} > {}",
                    toolName, metrics.parameterTransformations(), MAX_PARAMETER_TRANSFORMATIONS);
            withinThresholds = false;
        }

        return withinThresholds;
    }

    @Override
    public Map<String, Object> getPerformanceTrends(String toolName, Duration timeRange) {
        List<PerformanceRecord> records = toolMetrics.getOrDefault(toolName, List.of());
        Instant cutoffTime = Instant.now().minus(timeRange);

        List<PerformanceRecord> recentRecords = records.stream()
                .filter(r -> r.timestamp.isAfter(cutoffTime))
                .sorted((r1, r2) -> r1.timestamp.compareTo(r2.timestamp))
                .collect(Collectors.toList());

        if (recentRecords.isEmpty()) {
            return Map.of("toolName", toolName, "trend", "no_data");
        }

        Map<String, Object> trends = new HashMap<>();
        trends.put("toolName", toolName);
        trends.put("timeRange", timeRange.toString());
        trends.put("recordCount", recentRecords.size());

        // 计算趋势（简化实现）
        int halfSize = recentRecords.size() / 2;
        if (halfSize > 0) {
            List<PerformanceRecord> firstHalf = recentRecords.subList(0, halfSize);
            List<PerformanceRecord> secondHalf = recentRecords.subList(halfSize, recentRecords.size());

            Long firstHalfAvg = calculateAverageMs(
                    firstHalf.stream().map(r -> r.metrics.executionTimeMs()).collect(Collectors.toList())
            );
            Long secondHalfAvg = calculateAverageMs(
                    secondHalf.stream().map(r -> r.metrics.executionTimeMs()).collect(Collectors.toList())
            );

            String trend = secondHalfAvg > firstHalfAvg ? "degrading" : "improving";
            trends.put("executionTimeTrend", trend);
            trends.put("firstHalfAverage", firstHalfAvg);
            trends.put("secondHalfAverage", secondHalfAvg);
        }

        return trends;
    }

    @Override
    public String generatePerformanceReport(Duration timeRange) {
        StringBuilder report = new StringBuilder();
        report.append("Performance Report\n");
        report.append("==================\n");
        report.append("Time Range: ").append(timeRange.toString()).append("\n\n");

        // 系统整体统计
        Map<String, Object> systemStats = getSystemPerformanceStats();
        report.append("System Overview:\n");
        report.append("- Total Records: ").append(systemStats.get("totalRecords")).append("\n");
        report.append("- Average Execution Time: ").append(systemStats.get("systemAverageExecutionTime")).append("\n");
        report.append("- Cache Hit Rate: ").append(String.format("%.2f%%", (Double) systemStats.get("systemCacheHitRate") * 100)).append("\n\n");

        // 按工具统计
        report.append("Tool Performance:\n");
        for (String toolName : toolMetrics.keySet()) {
            Map<String, Object> toolStats = getToolPerformanceStats(toolName);
            report.append("- ").append(toolName).append(":\n");
            report.append("  Records: ").append(toolStats.get("recordCount")).append("\n");
            report.append("  Avg Execution Time: ").append(toolStats.get("averageExecutionTime")).append("\n");
            report.append("  Cache Hit Rate: ").append(String.format("%.2f%%", (Double) toolStats.get("cacheHitRate") * 100)).append("\n");
        }

        return report.toString();
    }

    private Duration calculateAverageDuration(List<Duration> durations) {
        if (durations.isEmpty()) {
            return Duration.ZERO;
        }

        long totalMillis = durations.stream().mapToLong(Duration::toMillis).sum();
        return Duration.ofMillis(totalMillis / durations.size());
    }

    private Long calculateAverageMs(List<Long> times) {
        if (times.isEmpty()) {
            return 0L;
        }

        return times.stream().mapToLong(Long::longValue).sum() / times.size();
    }

    /**
     * 性能记录内部类
     */
    private record PerformanceRecord(
            String toolName,
            String methodName,
            PerformanceMetrics metrics,
            Instant timestamp
    ) {
    }
}