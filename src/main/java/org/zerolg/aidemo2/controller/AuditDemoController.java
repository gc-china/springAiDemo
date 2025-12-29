package org.zerolg.aidemo2.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zerolg.aidemo2.audit.model.*;
import org.zerolg.aidemo2.audit.service.AuditService;
import org.zerolg.aidemo2.audit.service.DecisionContextManager;
import org.zerolg.aidemo2.audit.service.ParameterChainRecorder;
import org.zerolg.aidemo2.audit.service.PerformanceMonitor;
import org.zerolg.aidemo2.common.ApiResponse;
import org.zerolg.aidemo2.common.EnhancedToolExecutionResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 审计系统演示控制器
 * 提供审计功能的演示和测试接口
 */
@RestController
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = false)
@RequestMapping("/api/audit/demo")
public class AuditDemoController {

    @Autowired
    private AuditService auditService;

    @Autowired
    private ParameterChainRecorder parameterChainRecorder;

    @Autowired
    private DecisionContextManager decisionContextManager;

    @Autowired
    private PerformanceMonitor performanceMonitor;

    /**
     * 演示完整的审计流程
     */
    @PostMapping("/full-audit-demo")
    public ResponseEntity<ApiResponse<EnhancedToolExecutionResult>> fullAuditDemo(
            @RequestBody DemoRequest request) {

        String executionId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        String sessionId = request.sessionId() != null ? request.sessionId() : "demo-session";
        String userId = request.userId() != null ? request.userId() : "demo-user";

        Instant startTime = Instant.now();

        try {
            // 1. 开始审计
            ToolExecutionAudit audit = auditService.startExecution(
                    executionId, traceId, sessionId, userId,
                    "DemoTool", "fullAuditDemo",
                    Map.of("input", request.input(), "operation", request.operation())
            );

            // 2. 模拟参数转换链
            ParameterChain chain = ParameterChain.create(executionId,
                    Map.of("input", request.input(), "operation", request.operation()));

            // 添加标准化转换
            if (request.input() != null && !request.input().trim().equals(request.input())) {
                ParameterTransformation normalization = ParameterTransformation.create(
                        "input", request.input(), request.input().trim(),
                        "normalization", 0.95, "Trimmed whitespace"
                );
                chain = chain.addTransformation(normalization);
            }

            // 添加实体解析转换
            String resolvedInput = resolveInput(request.input());
            if (!resolvedInput.equals(request.input().trim())) {
                ParameterTransformation resolution = ParameterTransformation.create(
                        "input", request.input().trim(), resolvedInput,
                        "entity_resolution", 0.8, "Resolved to standard format"
                );
                chain = chain.addTransformation(resolution);
            }

            parameterChainRecorder.recordParameterChain(executionId, chain);
            auditService.updateParameterChain(executionId, chain);

            // 3. 模拟决策过程
            DecisionContext decisionContext = DecisionContext.create(
                    Map.of("input", resolvedInput, "operation", request.operation(), "toolName", "DemoTool"),
                    request.operation(), 0.9
            );

            decisionContextManager.saveDecisionContext(sessionId, decisionContext);
            auditService.updateDecisionContext(executionId, decisionContext);

            // 4. 执行业务逻辑
            String result = executeOperation(resolvedInput, request.operation());

            // 5. 计算性能指标
            Duration executionTime = Duration.between(startTime, Instant.now());
            PerformanceMetrics metrics = PerformanceMetrics.create(executionTime.toMillis())
                    .withParameterCorrection(50L, chain.steps().size())
                    .withCacheHit(false);

            performanceMonitor.recordExecutionMetrics("DemoTool", "fullAuditDemo", metrics);
            auditService.updateMetrics(executionId, metrics);

            // 6. 完成审计
            auditService.completeExecution(
                    executionId, "ok", result, null,
                    Map.of("input", resolvedInput, "operation", request.operation()),
                    executionTime.toMillis()
            );

            // 7. 创建增强结果
            AuditMetadata auditMetadata = AuditMetadata.create(
                    executionId, traceId, sessionId, userId, "DemoTool", "fullAuditDemo"
            );

            EnhancedToolExecutionResult enhancedResult = EnhancedToolExecutionResult
                    .success(result, "Demo operation completed successfully")
                    .withAuditMetadata(auditMetadata)
                    .withParameterChain(chain)
                    .withDecisionContext(decisionContext)
                    .withMetrics(metrics);

            return ResponseEntity.ok(ApiResponse.success(enhancedResult));

        } catch (Exception e) {
            Duration executionTime = Duration.between(startTime, Instant.now());

            auditService.completeExecution(
                    executionId, "error", null, e.getMessage(),
                    Map.of("input", request.input(), "operation", request.operation()),
                    executionTime.toMillis()
            );

            EnhancedToolExecutionResult errorResult = EnhancedToolExecutionResult
                    .error("Demo operation failed: " + e.getMessage());

            return ResponseEntity.ok(ApiResponse.success(errorResult));
        }
    }

    /**
     * 演示决策建议功能
     */
    @PostMapping("/decision-suggestion-demo")
    public ResponseEntity<ApiResponse<DecisionSuggestion>> decisionSuggestionDemo(
            @RequestBody DecisionDemoRequest request) {

        // 创建一些历史决策数据
        createSampleDecisionHistory(request.sessionId());

        // 请求决策建议
        DecisionRequest decisionRequest = DecisionRequest.create(
                request.sessionId(), "DemoTool",
                Map.of("input", request.input(), "context", request.context())
        );

        DecisionSuggestion suggestion = decisionContextManager.suggestConsistentDecision(decisionRequest);

        return ResponseEntity.ok(ApiResponse.success(suggestion));
    }

    /**
     * 演示性能监控功能
     */
    @GetMapping("/performance-demo/{toolName}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> performanceDemo(@PathVariable String toolName) {

        // 创建一些示例性能数据
        createSamplePerformanceData(toolName);

        // 获取性能统计
        Map<String, Object> stats = performanceMonitor.getToolPerformanceStats(toolName);

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 演示参数模式分析功能
     */
    @GetMapping("/parameter-patterns-demo/{toolName}")
    public ResponseEntity<ApiResponse<ParameterPatternAnalysis>> parameterPatternsDemo(@PathVariable String toolName) {

        // 创建一些示例参数转换数据
        createSampleParameterData(toolName);

        // 分析参数模式
        ParameterPatternAnalysis analysis = parameterChainRecorder.analyzePatterns(toolName, Duration.ofDays(1));

        return ResponseEntity.ok(ApiResponse.success(analysis));
    }

    // 辅助方法
    private String resolveInput(String input) {
        if (input == null) return "";

        // 简单的实体解析逻辑
        String trimmed = input.trim().toLowerCase();

        // 模拟一些常见的解析规则
        switch (trimmed) {
            case "iphone", "苹果手机", "apple phone":
                return "iPhone";
            case "laptop", "笔记本", "notebook":
                return "Laptop";
            case "mouse", "鼠标":
                return "Mouse";
            default:
                return input.trim();
        }
    }

    private String executeOperation(String input, String operation) {
        return switch (operation.toLowerCase()) {
            case "query" -> "Queried: " + input;
            case "create" -> "Created: " + input;
            case "update" -> "Updated: " + input;
            case "delete" -> "Deleted: " + input;
            default -> "Processed: " + input + " with operation: " + operation;
        };
    }

    private void createSampleDecisionHistory(String sessionId) {
        // 创建一些示例决策历史
        for (int i = 1; i <= 5; i++) {
            DecisionContext context = DecisionContext.create(
                    Map.of("input", "sample" + i, "context", "demo", "toolName", "DemoTool"),
                    "query", 0.8 + i * 0.02
            );
            decisionContextManager.saveDecisionContext(sessionId, context);
        }
    }

    private void createSamplePerformanceData(String toolName) {
        // 创建一些示例性能数据
        for (int i = 1; i <= 10; i++) {
            PerformanceMetrics metrics = PerformanceMetrics.create(100L + i * 50L)
                    .withParameterCorrection(10L + i * 5L, i % 3)
                    .withCacheHit(i % 2 == 0);

            performanceMonitor.recordExecutionMetrics(toolName, "demoMethod", metrics);
        }
    }

    private void createSampleParameterData(String toolName) {
        // 创建一些示例参数转换数据
        for (int i = 1; i <= 8; i++) {
            String executionId = "demo-exec-" + i;
            Map<String, Object> originalParams = Map.of("param", "value" + i);

            ParameterChain chain = ParameterChain.create(executionId, originalParams);

            // 添加标准化转换
            ParameterTransformation normalization = ParameterTransformation.create(
                    "param", "value" + i, "normalized_value" + i, "normalization", 0.9, "Demo normalization"
            );
            chain = chain.addTransformation(normalization);

            // 随机添加实体解析转换
            if (i % 2 == 0) {
                ParameterTransformation resolution = ParameterTransformation.create(
                        "param", "normalized_value" + i, "resolved_value" + i, "entity_resolution", 0.8, "Demo resolution"
                );
                chain = chain.addTransformation(resolution);
            }

            parameterChainRecorder.recordParameterChain(executionId, chain);
        }
    }

    // 请求对象定义
    public record DemoRequest(
            String input,
            String operation,
            String sessionId,
            String userId
    ) {
    }

    public record DecisionDemoRequest(
            String input,
            String context,
            String sessionId
    ) {
    }
}