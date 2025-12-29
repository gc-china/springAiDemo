package org.zerolg.aidemo2.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.zerolg.aidemo2.audit.model.AuditMetadata;
import org.zerolg.aidemo2.audit.model.DecisionContext;
import org.zerolg.aidemo2.audit.model.PerformanceMetrics;
import org.zerolg.aidemo2.audit.service.AuditService;
import org.zerolg.aidemo2.audit.service.DecisionContextManager;
import org.zerolg.aidemo2.audit.service.PerformanceMonitor;
import org.zerolg.aidemo2.common.EnhancedToolExecutionResult;
import org.zerolg.aidemo2.common.ToolExecutionResult;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.service.stock.EnhancedStockQueryService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 带审计功能的库存工具
 * 演示如何在工具中集成完整的审计和可追溯功能
 */
@Configuration
@ConditionalOnProperty(name = "tools.inventory.audited.enabled", havingValue = "true", matchIfMissing = false)
public class AuditedInventoryTools {

    private static final Logger logger = LoggerFactory.getLogger(AuditedInventoryTools.class);

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private EnhancedStockQueryService stockQueryService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private DecisionContextManager decisionContextManager;

    @Autowired
    private PerformanceMonitor performanceMonitor;

    @Bean
    @Description("带完整审计功能的库存查询工具")
    public Function<AuditedStockQueryRequest, String> auditedQueryStock() {
        return request -> {
            String executionId = UUID.randomUUID().toString();
            String traceId = UUID.randomUUID().toString();
            String sessionId = request.sessionId() != null ? request.sessionId() : "default-session";
            String userId = request.userId() != null ? request.userId() : "system";

            Instant startTime = Instant.now();

            try {
                // 开始审计
                auditService.startExecution(
                        executionId, traceId, sessionId, userId,
                        "AuditedInventoryTools", "auditedQueryStock",
                        Map.of("product", request.product())
                );

                // 执行库存查询
                ToolExecutionResult result = stockQueryService.queryStock(
                        request.product(), null, null, null, "exact"
                );

                // 计算执行时间
                Duration executionTime = Duration.between(startTime, Instant.now());

                // 创建审计元数据
                AuditMetadata auditMetadata = AuditMetadata.create(
                        executionId, traceId, sessionId, userId,
                        "AuditedInventoryTools", "auditedQueryStock"
                );

                // 创建性能指标
                PerformanceMetrics metrics = PerformanceMetrics.create(executionTime.toMillis());

                // 记录性能指标
                performanceMonitor.recordExecutionMetrics(
                        "AuditedInventoryTools", "auditedQueryStock", metrics
                );

                // 创建决策上下文（如果需要）
                DecisionContext decisionContext = null;
                if (result.isAmbiguous()) {
                    decisionContext = DecisionContext.create(
                            Map.of("product", request.product(), "toolName", "AuditedInventoryTools"),
                            "ambiguous_query", 0.7
                    );
                    decisionContextManager.saveDecisionContext(sessionId, decisionContext);
                }

                // 完成审计
                auditService.completeExecution(
                        executionId, result.status(), result.payload(),
                        null, Map.of("product", request.product()),
                        executionTime.toMillis()
                );

                // 创建增强结果
                EnhancedToolExecutionResult enhancedResult = EnhancedToolExecutionResult
                        .fromLegacy(result)
                        .withAuditMetadata(auditMetadata)
                        .withDecisionContext(decisionContext)
                        .withMetrics(metrics);

                logger.info("Audited stock query completed: product={}, status={}, executionTime={}ms",
                        request.product(), result.status(), executionTime.toMillis());

                return enhancedResult.toJson();

            } catch (Exception e) {
                Duration executionTime = Duration.between(startTime, Instant.now());

                // 记录失败的审计
                auditService.completeExecution(
                        executionId, "error", null, e.getMessage(),
                        Map.of("product", request.product()),
                        executionTime.toMillis()
                );

                logger.error("Audited stock query failed: product={}", request.product(), e);

                EnhancedToolExecutionResult errorResult = EnhancedToolExecutionResult
                        .error("Stock query failed: " + e.getMessage())
                        .withAuditMetadata(AuditMetadata.create(
                                executionId, traceId, sessionId, userId,
                                "AuditedInventoryTools", "auditedQueryStock"
                        ))
                        .withMetrics(PerformanceMetrics.create(executionTime.toMillis()));

                return errorResult.toJson();
            }
        };
    }

    @Bean
    @Description("带智能决策建议的库存调拨工具")
    public Function<AuditedTransferRequest, String> auditedTransferStock() {
        return request -> {
            String executionId = UUID.randomUUID().toString();
            String traceId = UUID.randomUUID().toString();
            String sessionId = request.sessionId() != null ? request.sessionId() : "default-session";
            String userId = request.userId() != null ? request.userId() : "system";

            Instant startTime = Instant.now();

            try {
                Map<String, Object> params = Map.of(
                        "product", request.product(),
                        "fromWarehouse", request.fromWarehouse(),
                        "toWarehouse", request.toWarehouse(),
                        "quantity", request.quantity(),
                        "confirmed", request.confirmed()
                );

                // 开始审计
                auditService.startExecution(
                        executionId, traceId, sessionId, userId,
                        "AuditedInventoryTools", "auditedTransferStock", params
                );

                // 获取决策建议
                var decisionRequest = new org.zerolg.aidemo2.audit.model.DecisionRequest(
                        sessionId, "AuditedInventoryTools", params, Map.of()
                );
                var suggestion = decisionContextManager.suggestConsistentDecision(decisionRequest);

                // 执行调拨
                boolean transferSuccess = inventoryService.transferStock(
                        request.product(), request.fromWarehouse(),
                        request.toWarehouse(), request.quantity()
                );

                // 转换为ToolExecutionResult
                ToolExecutionResult result = transferSuccess ?
                        ToolExecutionResult.success("调拨成功", "库存调拨已完成") :
                        ToolExecutionResult.error("调拨失败库存不足或调拨异常");

                Duration executionTime = Duration.between(startTime, Instant.now());

                // 创建决策上下文
                DecisionContext decisionContext = DecisionContext.create(
                        Map.of("product", request.product(), "fromWarehouse", request.fromWarehouse(),
                                "toWarehouse", request.toWarehouse(), "quantity", request.quantity(),
                                "toolName", "AuditedInventoryTools"),
                        result.status(), suggestion.confidence()
                ).withAlternatives(Map.of("alternatives", suggestion.alternatives()));

                decisionContextManager.saveDecisionContext(sessionId, decisionContext);

                // 更新决策结果
                decisionContextManager.updateDecisionOutcome(
                        sessionId, "AuditedInventoryTools", result.status(), result.isSuccess()
                );

                // 完成审计
                auditService.completeExecution(
                        executionId, result.status(), result.payload(),
                        null, params, executionTime.toMillis()
                );

                // 创建增强结果
                EnhancedToolExecutionResult enhancedResult = EnhancedToolExecutionResult
                        .fromLegacy(result)
                        .withAuditMetadata(AuditMetadata.create(
                                executionId, traceId, sessionId, userId,
                                "AuditedInventoryTools", "auditedTransferStock"
                        ))
                        .withDecisionContext(decisionContext)
                        .withMetrics(PerformanceMetrics.create(executionTime.toMillis()));

                // 添加决策建议到解释中
                if (suggestion.confidence() > 0.5) {
                    String enhancedExplain = result.explain() +
                            " (Decision confidence: " + String.format("%.1f%%", suggestion.confidence() * 100) +
                            " based on " + suggestion.similarCases().size() + " similar cases)";

                    enhancedResult = new EnhancedToolExecutionResult(
                            enhancedResult.status(), enhancedResult.payload(), enhancedExplain,
                            enhancedResult.auditMetadata(), enhancedResult.parameterChain(),
                            enhancedResult.decisionContext(), enhancedResult.metrics()
                    );
                }

                logger.info("Audited stock transfer completed: product={}, status={}, confidence={}",
                        request.product(), result.status(), suggestion.confidence());

                return enhancedResult.toJson();

            } catch (Exception e) {
                Duration executionTime = Duration.between(startTime, Instant.now());

                auditService.completeExecution(
                        executionId, "error", null, e.getMessage(),
                        Map.of("product", request.product()), executionTime.toMillis()
                );

                logger.error("Audited stock transfer failed: product={}", request.product(), e);

                return EnhancedToolExecutionResult
                        .error("Stock transfer failed: " + e.getMessage())
                        .toJson();
            }
        };
    }

    // 请求对象定义
    public record AuditedStockQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称或ID")
            String product,

            @JsonPropertyDescription("会话ID，用于审计追踪")
            String sessionId,

            @JsonPropertyDescription("用户ID，用于审计追踪")
            String userId
    ) {
    }

    public record AuditedTransferRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称或ID")
            String product,

            @JsonProperty(required = true)
            @JsonPropertyDescription("源仓库")
            String fromWarehouse,

            @JsonProperty(required = true)
            @JsonPropertyDescription("目标仓库")
            String toWarehouse,

            @JsonProperty(required = true)
            @JsonPropertyDescription("数量")
            Integer quantity,

            @JsonPropertyDescription("是否已确认")
            Boolean confirmed,

            @JsonPropertyDescription("会话ID，用于审计追踪")
            String sessionId,

            @JsonPropertyDescription("用户ID，用于审计追踪")
            String userId
    ) {
    }
}