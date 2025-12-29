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
import org.zerolg.aidemo2.audit.service.EnhancedParameterCorrectionService;
import org.zerolg.aidemo2.common.EnhancedToolExecutionResult;
import org.zerolg.aidemo2.common.ToolExecutionResult;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.CorrectionStatus;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.service.stock.EnhancedStockQueryService;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 集成现有参数清洗层的库存工具
 * 演示如何在现有工具中无缝集成审计功能
 */
@Configuration
@ConditionalOnProperty(name = "tools.inventory.integrated.enabled", havingValue = "true", matchIfMissing = true)
public class IntegratedInventoryTools {

    private static final Logger logger = LoggerFactory.getLogger(IntegratedInventoryTools.class);

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private EnhancedStockQueryService stockQueryService;

    @Autowired
    private EnhancedParameterCorrectionService enhancedCorrectionService;

    @Bean
    @Description("集成参数清洗和审计的智能库存查询工具")
    public Function<IntegratedStockQueryRequest, String> integratedQueryStock() {
        return request -> {
            String executionId = UUID.randomUUID().toString();

            try {
                logger.info("开始集成库存查询: product={}, executionId={}", request.product(), executionId);

                // 1. 使用增强的参数修正服务（集成现有清洗层）
                CorrectionResult correctionResult = enhancedCorrectionService.correctParameterWithDetailedAudit(
                        "product",
                        String.class,
                        request.product(),
                        null,
                        "integratedQueryStock",
                        executionId
                );

                // 2. 根据修正结果决定如何处理
                String finalProduct;
                String explanation;

                switch (correctionResult.status()) {
                    case SUCCESS:
                        finalProduct = (String) correctionResult.correctedValue();
                        explanation = "产品名称已修正: " + String.join(", ", correctionResult.corrections());
                        logger.info("参数修正成功: {} -> {}", request.product(), finalProduct);
                        break;

                    case NEEDS_CONFIRMATION:
                        // 返回需要确认的结果
                        return EnhancedToolExecutionResult.ambiguous(
                                correctionResult.metadata().get("candidates"),
                                "产品名称存在歧义，请选择: " + correctionResult.corrections()
                        ).toJson();

                    case FAILED:
                        // 参数修正失败时，使用原始参数继续执行（降级处理）
                        finalProduct = request.product();
                        explanation = "参数修正失败，使用原始参数: " + String.join(", ", correctionResult.corrections());
                        logger.warn("参数修正失败，降级使用原始参数: {}", request.product());
                        break;

                    case NO_CORRECTION_NEEDED:
                    default:
                        finalProduct = request.product();
                        explanation = "产品名称无需修正";
                        break;
                }

                // 3. 执行库存查询
                ToolExecutionResult queryResult = stockQueryService.queryStock(
                        finalProduct, null, null, null, "exact"
                );

                // 4. 增强结果说明
                String enhancedExplanation = queryResult.explain() + " | " + explanation;
                if (correctionResult.confidence() < 1.0) {
                    enhancedExplanation += String.format(" (修正置信度: %.1f%%)", correctionResult.confidence() * 100);
                }

                // 5. 返回增强结果
                EnhancedToolExecutionResult enhancedResult = EnhancedToolExecutionResult.fromLegacy(queryResult);
                return new EnhancedToolExecutionResult(
                        enhancedResult.status(),
                        enhancedResult.payload(),
                        enhancedExplanation,
                        enhancedResult.auditMetadata(),
                        enhancedResult.parameterChain(),
                        enhancedResult.decisionContext(),
                        enhancedResult.metrics()
                ).toJson();

            } catch (Exception e) {
                logger.error("集成库存查询失败: product={}", request.product(), e);
                return EnhancedToolExecutionResult.error("查询失败: " + e.getMessage()).toJson();
            }
        };
    }

    @Bean
    @Description("集成参数清洗和审计的智能库存调拨工具")
    public Function<IntegratedTransferRequest, String> integratedTransferStock() {
        return request -> {
            String executionId = UUID.randomUUID().toString();

            try {
                logger.info("开始集成库存调拨: product={}, from={}, to={}, quantity={}",
                        request.product(), request.fromWarehouse(), request.toWarehouse(), request.quantity());

                // 1. 批量修正所有参数
                Map<String, Object> parameters = Map.of(
                        "product", request.product(),
                        "fromWarehouse", request.fromWarehouse(),
                        "toWarehouse", request.toWarehouse(),
                        "quantity", request.quantity()
                );

                Map<String, Class<?>> parameterTypes = Map.of(
                        "product", String.class,
                        "fromWarehouse", String.class,
                        "toWarehouse", String.class,
                        "quantity", Integer.class
                );

                Map<String, CorrectionResult> correctionResults = enhancedCorrectionService.correctParametersWithAudit(
                        parameters, parameterTypes, null, "integratedTransferStock", executionId
                );

                // 2. 检查修正结果
                StringBuilder explanationBuilder = new StringBuilder();
                boolean hasErrors = false;
                boolean needsConfirmation = false;

                String finalProduct = request.product();
                String finalFromWarehouse = request.fromWarehouse();
                String finalToWarehouse = request.toWarehouse();
                Integer finalQuantity = request.quantity();

                for (Map.Entry<String, CorrectionResult> entry : correctionResults.entrySet()) {
                    String paramName = entry.getKey();
                    CorrectionResult result = entry.getValue();

                    switch (result.status()) {
                        case SUCCESS:
                            explanationBuilder.append(String.format("%s已修正; ", paramName));
                            // 更新最终值
                            switch (paramName) {
                                case "product":
                                    finalProduct = (String) result.correctedValue();
                                    break;
                                case "fromWarehouse":
                                    finalFromWarehouse = (String) result.correctedValue();
                                    break;
                                case "toWarehouse":
                                    finalToWarehouse = (String) result.correctedValue();
                                    break;
                                case "quantity":
                                    finalQuantity = (Integer) result.correctedValue();
                                    break;
                            }
                            break;

                        case NEEDS_CONFIRMATION:
                            needsConfirmation = true;
                            explanationBuilder.append(String.format("%s存在歧义; ", paramName));
                            break;

                        case FAILED:
                            hasErrors = true;
                            explanationBuilder.append(String.format("%s修正失败; ", paramName));
                            break;
                    }
                }

                // 3. 根据修正结果决定如何处理
                if (hasErrors) {
                    // 参数修正失败时，使用原始参数继续执行（降级处理）
                    logger.warn("参数修正失败，降级使用原始参数: {}", explanationBuilder.toString());
                    explanationBuilder.append("(降级处理) ");
                    // 继续使用原始参数执行
                }

                if (needsConfirmation && !Boolean.TRUE.equals(request.confirmed())) {
                    return EnhancedToolExecutionResult.needsConfirmation(
                            Map.of(
                                    "product", finalProduct,
                                    "fromWarehouse", finalFromWarehouse,
                                    "toWarehouse", finalToWarehouse,
                                    "quantity", finalQuantity,
                                    "correctionResults", correctionResults
                            ),
                            "参数存在歧义，请确认修正后的值: " + explanationBuilder.toString()
                    ).toJson();
                }

                // 4. 执行库存调拨
                boolean transferSuccess = inventoryService.transferStock(
                        finalProduct, finalFromWarehouse, finalToWarehouse, finalQuantity
                );

                // 转换为ToolExecutionResult
                ToolExecutionResult transferResult = transferSuccess ?
                        ToolExecutionResult.success("调拨成功", "库存调拨已完成") :
                        ToolExecutionResult.error("调拨失败库存不足或调拨异常");

                // 5. 增强结果说明
                String enhancedExplanation = transferResult.explain() + " | 参数处理: " + explanationBuilder.toString();

                // 计算整体置信度
                double overallConfidence = correctionResults.values().stream()
                        .mapToDouble(CorrectionResult::confidence)
                        .average()
                        .orElse(1.0);

                if (overallConfidence < 1.0) {
                    enhancedExplanation += String.format(" (整体置信度: %.1f%%)", overallConfidence * 100);
                }

                // 6. 返回增强结果
                EnhancedToolExecutionResult enhancedResult = EnhancedToolExecutionResult.fromLegacy(transferResult);
                return new EnhancedToolExecutionResult(
                        enhancedResult.status(),
                        enhancedResult.payload(),
                        enhancedExplanation,
                        enhancedResult.auditMetadata(),
                        enhancedResult.parameterChain(),
                        enhancedResult.decisionContext(),
                        enhancedResult.metrics()
                ).toJson();

            } catch (Exception e) {
                logger.error("集成库存调拨失败", e);
                return EnhancedToolExecutionResult.error("调拨失败: " + e.getMessage()).toJson();
            }
        };
    }

    @Bean
    @Description("演示参数清洗链路追踪的工具")
    public Function<ParameterTraceRequest, String> parameterTraceDemo() {
        return request -> {
            String executionId = UUID.randomUUID().toString();

            try {
                // 使用增强参数修正服务处理复杂参数
                CorrectionResult result = enhancedCorrectionService.correctParameterWithDetailedAudit(
                        "complexParam",
                        String.class,
                        request.input(),
                        null,
                        "parameterTraceDemo",
                        executionId
                );

                // 获取参数转换统计
                Map<String, Object> stats = enhancedCorrectionService.getEnhancedCorrectionStatistics();

                // 构建详细的追踪信息
                Map<String, Object> traceInfo = Map.of(
                        "executionId", executionId,
                        "originalInput", request.input(),
                        "correctedValue", result.correctedValue(),
                        "correctionStatus", result.status().toString(),
                        "confidence", result.confidence(),
                        "corrections", result.corrections(),
                        "metadata", result.metadata(),
                        "systemStats", stats
                );

                return EnhancedToolExecutionResult.success(
                        traceInfo,
                        String.format("参数追踪完成: %s -> %s (置信度: %.1f%%)",
                                request.input(), result.correctedValue(), result.confidence() * 100)
                ).toJson();

            } catch (Exception e) {
                logger.error("参数追踪演示失败", e);
                return EnhancedToolExecutionResult.error("追踪失败: " + e.getMessage()).toJson();
            }
        };
    }

    // 请求对象定义
    public record IntegratedStockQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称或ID，支持模糊匹配和自动修正")
            String product
    ) {
    }

    public record IntegratedTransferRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称或ID，支持自动修正")
            String product,

            @JsonProperty(required = true)
            @JsonPropertyDescription("源仓库，支持自动修正")
            String fromWarehouse,

            @JsonProperty(required = true)
            @JsonPropertyDescription("目标仓库，支持自动修正")
            String toWarehouse,

            @JsonProperty(required = true)
            @JsonPropertyDescription("数量，支持自动修正")
            Integer quantity,

            @JsonPropertyDescription("是否已确认修正后的参数")
            Boolean confirmed
    ) {
    }

    public record ParameterTraceRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("需要追踪清洗过程的输入参数")
            String input
    ) {
    }
}