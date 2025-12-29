package org.zerolg.aidemo2.audit.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zerolg.aidemo2.audit.model.*;
import org.zerolg.aidemo2.audit.service.AuditService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 审计系统REST API控制器
 * 提供审计数据查询和监控接口
 */
@RestController
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = false)
@RequestMapping("/api/audit")
@CrossOrigin(originPatterns = "*")
public class AuditController {

    @Autowired
    private AuditService auditService;

    /**
     * 查询审计轨迹
     */
    @GetMapping("/trail")
    public ResponseEntity<List<ToolExecutionAudit>> queryAuditTrail(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {

        AuditQuery query = new AuditQuery(
                sessionId, userId, toolName, statuses, startTime, endTime, offset, limit
        );

        List<ToolExecutionAudit> results = auditService.queryAuditTrail(query);
        return ResponseEntity.ok(results);
    }

    /**
     * 获取会话审计摘要
     */
    @GetMapping("/session/{sessionId}/summary")
    public ResponseEntity<SessionAuditSummary> getSessionSummary(@PathVariable String sessionId) {
        SessionAuditSummary summary = auditService.getSessionSummary(sessionId);
        return ResponseEntity.ok(summary);
    }

    /**
     * 获取系统审计统计
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getAuditStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {

        if (since == null) {
            since = Instant.now().minusSeconds(24 * 60 * 60); // 默认最近24小时
        }

        // 查询最近的审计记录
        AuditQuery query = new AuditQuery(null, null, null, null, since, null, 0, 1000);
        List<ToolExecutionAudit> audits = auditService.queryAuditTrail(query);

        // 计算统计信息
        Map<String, Object> statistics = Map.of(
                "totalExecutions", audits.size(),
                "successfulExecutions", audits.stream().mapToInt(a -> "ok".equals(a.status()) ? 1 : 0).sum(),
                "failedExecutions", audits.stream().mapToInt(a -> "error".equals(a.status()) ? 1 : 0).sum(),
                "ambiguousExecutions", audits.stream().mapToInt(a -> "ambiguous".equals(a.status()) ? 1 : 0).sum(),
                "averageExecutionTime", audits.stream().mapToLong(ToolExecutionAudit::executionTimeMs).average().orElse(0.0),
                "toolUsage", audits.stream().collect(
                        java.util.stream.Collectors.groupingBy(
                                ToolExecutionAudit::toolName,
                                java.util.stream.Collectors.counting()
                        )
                ),
                "timeRange", Map.of(
                        "start", since,
                        "end", Instant.now()
                )
        );

        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取工具执行详情
     */
    @GetMapping("/execution/{executionId}")
    public ResponseEntity<ToolExecutionAudit> getExecutionDetails(@PathVariable String executionId) {
        AuditQuery query = new AuditQuery(null, null, null, null, null, null, 0, 1);
        List<ToolExecutionAudit> results = auditService.queryAuditTrail(query);

        ToolExecutionAudit execution = results.stream()
                .filter(audit -> executionId.equals(audit.executionId()))
                .findFirst()
                .orElse(null);

        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(execution);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "auditService", auditService.getClass().getSimpleName(),
                "timestamp", Instant.now()
        );

        return ResponseEntity.ok(health);
    }
}