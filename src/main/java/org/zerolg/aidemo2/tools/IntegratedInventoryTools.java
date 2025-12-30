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
import org.zerolg.aidemo2.audit.service.AuditService;
import org.zerolg.aidemo2.audit.service.EnhancedParameterCorrectionService;
import org.zerolg.aidemo2.common.EnhancedToolExecutionResult;
import org.zerolg.aidemo2.common.ToolExecutionResult;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.CorrectionStatus;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.service.stock.EnhancedStockQueryService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 集成库存管理工具类
 *
 * 这是一个综合性的AI工具集合，展示了如何将多个功能模块整合到一起：
 * 1. 智能参数纠错：自动修正用户输入的参数错误
 * 2. 审计日志记录：记录所有工具调用的详细过程
 * 3. 库存业务逻辑：实现库存查询、调拨等核心功能
 * 4. 错误处理机制：提供完善的异常处理和降级策略
 *
 * 主要特性：
 * - 参数智能纠错：支持模糊匹配、中文识别、数字转换等
 * - 全链路审计：记录参数修正过程、执行时间、结果状态等
 * - 业务逻辑集成：与现有库存服务无缝集成
 * - 用户交互优化：支持参数确认、歧义处理等交互场景
 *
 * 工具注册机制：
 * - 使用@Bean注解将Function注册为Spring AI工具
 * - 通过@Description提供工具描述，帮助AI理解工具用途
 * - 支持条件化启用，可通过配置文件控制工具是否生效
 *
 * @author zerolg
 */
@Configuration
@ConditionalOnProperty(name = "tools.inventory.integrated.enabled", havingValue = "true", matchIfMissing = true)
public class IntegratedInventoryTools {

    private static final Logger logger = LoggerFactory.getLogger(IntegratedInventoryTools.class);

    // 依赖注入的服务组件
    @Autowired
    private InventoryService inventoryService;                    // 基础库存服务

    @Autowired
    private EnhancedStockQueryService stockQueryService;         // 增强库存查询服务

    @Autowired(required = false)
    private EnhancedParameterCorrectionService enhancedCorrectionService; // 参数纠错服务（可选）

    @Autowired(required = false)
    private AuditService auditService;                           // 审计服务（可选）

    /**
     * 集成智能库存查询工具
     * <p>
     * 这是一个展示完整AI工具开发流程的示例，包含以下核心功能：
     * <p>
     * 1. 参数智能纠错：
     * - 自动识别和修正用户输入的产品名称错误
     * - 支持模糊匹配、拼写纠错、同义词替换等
     * - 处理中英文混合输入、缩写展开等复杂场景
     * <p>
     * 2. 全链路审计：
     * - 记录工具调用的完整生命周期
     * - 追踪参数修正过程和决策依据
     * - 统计执行时间和性能指标
     * <p>
     * 3. 智能交互：
     * - 当参数存在歧义时，返回候选选项供用户选择
     * - 提供详细的修正说明和置信度信息
     * - 支持降级处理，确保服务可用性
     * <p>
     * 4. 业务逻辑集成：
     * - 与现有库存查询服务无缝集成
     * - 保持原有业务逻辑不变
     * - 增强结果展示和错误处理
     * <p>
     * 工作流程：
     * 1. 开始审计记录 → 2. 参数智能纠错 → 3. 执行库存查询
     * 4. 增强结果说明 → 5. 完成审计记录 → 6. 返回结果
     *
     * @return Spring AI工具函数，接收查询请求并返回JSON格式的结果
     */
    @Bean
    @Description("集成参数清洗和审计的智能库存查询工具")
    public Function<IntegratedStockQueryRequest, String> integratedQueryStock() {
        return request -> {
            logger.info("========== integratedQueryStock 被调用 ==========");
            logger.info("请求参数: product={}", request.product());

            // 生成唯一的执行ID，用于追踪整个调用过程
            String executionId = UUID.randomUUID().toString();
            Instant startTime = Instant.now();

            try {
                logger.info("开始集成库存查询: product={}, executionId={}", request.product(), executionId);

                // ==================== 1. 开始审计记录 ====================
                // 记录工具调用的开始时间、参数、上下文等信息
                if (auditService != null) {
                    logger.info("审计服务可用，开始记录执行: executionId={}", executionId);
                    auditService.startExecution(
                            executionId, UUID.randomUUID().toString(),
                            "session-" + System.currentTimeMillis(), "user-default",
                            "IntegratedInventoryTools", "integratedQueryStock",
                            Map.of("product", request.product())
                    );
                } else {
                    logger.warn("审计服务不可用 (auditService == null)，跳过审计记录");
                }

                // 初始化参数处理变量
                String finalProduct = request.product();  // 最终使用的产品名称
                String explanation = "直接使用原始参数";   // 参数处理说明
                CorrectionResult correctionResult = null; // 参数纠错结果

                // ==================== 2. 智能参数纠错 ====================
                // 如果参数纠错服务可用，尝试修正用户输入的参数
                if (enhancedCorrectionService != null) {
                    logger.info("增强参数修正服务可用，开始参数修正");
                    try {
                        // 调用参数纠错服务，获取修正结果
                        correctionResult = enhancedCorrectionService.correctParameterWithDetailedAudit(
                                "product",              // 参数名称
                                String.class,           // 参数类型
                                request.product(),      // 原始参数值
                                null,                   // 额外约束条件
                                "integratedQueryStock", // 调用方法名
                                executionId             // 执行ID，用于审计关联
                        );

                        // ==================== 3. 处理纠错结果 ====================
                        // 根据不同的纠错状态采取相应的处理策略
                        switch (correctionResult.status()) {
                            case SUCCESS:
                                // 参数纠错成功，使用修正后的值
                                finalProduct = (String) correctionResult.correctedValue();
                                explanation = "产品名称已修正: " + String.join(", ", correctionResult.corrections());
                                logger.info("参数修正成功: {} -> {}", request.product(), finalProduct);
                                break;

                            case NEEDS_CONFIRMATION:
                                // 参数存在歧义，需要用户确认
                                // 返回候选选项，让用户选择正确的选项
                                return EnhancedToolExecutionResult.ambiguous(
                                        correctionResult.metadata().get("candidates"),
                                        "产品名称存在歧义，请选择: " + correctionResult.corrections()
                                ).toJson();

                            case FAILED:
                                // 参数纠错失败，使用原始参数继续执行（降级处理）
                                logger.warn("参数修正失败，降级使用原始参数: {}", request.product());
                                explanation = "参数修正失败，使用原始参数";
                                break;

                            case NO_CORRECTION_NEEDED:
                            default:
                                // 参数无需修正，直接使用原始值
                                explanation = "产品名称无需修正";
                                break;
                        }
                    } catch (Exception e) {
                        // 参数纠错服务异常，降级使用原始参数
                        logger.error("参数修正服务异常，使用原始参数: {}", request.product(), e);
                        explanation = "参数修正服务异常，使用原始参数";
                    }
                } else {
                    logger.warn("增强参数修正服务不可用 (enhancedCorrectionService == null)，直接使用原始参数");
                }

                // ==================== 4. 执行库存查询 ====================
                // 使用最终确定的参数调用库存查询服务
                ToolExecutionResult queryResult = stockQueryService.queryStock(
                        finalProduct, null, null, null, "exact"
                );

                // ==================== 5. 增强结果说明 ====================
                // 将业务结果和参数处理过程结合，提供更丰富的说明信息
                String enhancedExplanation = queryResult.explain() + " | " + explanation;
                if (correctionResult != null && correctionResult.confidence() < 1.0) {
                    enhancedExplanation += String.format(" (修正置信度: %.1f%%)", correctionResult.confidence() * 100);
                }

                // ==================== 6. 完成审计记录 ====================
                // 记录工具调用的结束时间、结果、性能指标等信息
                Duration executionTime = Duration.between(startTime, Instant.now());
                if (auditService != null) {
                    logger.info("准备完成审计: executionId={}", executionId);
                    auditService.completeExecution(
                            executionId, queryResult.status(), queryResult.payload(), null,
                            Map.of("product", request.product(), "finalProduct", finalProduct),
                            executionTime.toMillis()
                    );
                    logger.info("审计完成调用已执行: executionId={}, executionTime={}ms", executionId, executionTime.toMillis());
                } else {
                    logger.warn("auditService 为 null，无法完成审计");
                }

                // ==================== 7. 返回增强结果 ====================
                // 将标准结果转换为增强结果格式，包含更多元数据
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

                // ==================== 异常处理和审计 ====================
                // 记录失败的审计信息，确保问题可追溯
                if (auditService != null) {
                    Duration executionTime = Duration.between(startTime, Instant.now());
                    auditService.completeExecution(
                            executionId, "error", null, e.getMessage(),
                            Map.of("product", request.product()),
                            executionTime.toMillis()
                    );
                }

                // 返回友好的错误信息
                return EnhancedToolExecutionResult.error("查询失败: " + e.getMessage()).toJson();
            }
        };
    }

    @Bean
    @Description("集成参数清洗和审计的智能库存调拨工具")
    public Function<IntegratedTransferRequest, String> integratedTransferStock() {
        return request -> {
            String executionId = UUID.randomUUID().toString();
            Instant startTime = Instant.now();

            try {
                logger.info("开始集成库存调拨: product={}, from={}, to={}, quantity={}",
                        request.product(), request.fromWarehouse(), request.toWarehouse(), request.quantity());

                // 开始审计
                if (auditService != null) {
                    auditService.startExecution(
                            executionId, UUID.randomUUID().toString(),
                            "session-" + System.currentTimeMillis(), "user-default",
                            "IntegratedInventoryTools", "integratedTransferStock",
                            Map.of("product", request.product(), "fromWarehouse", request.fromWarehouse(),
                                    "toWarehouse", request.toWarehouse(), "quantity", request.quantity())
                    );
                }

                String finalProduct = request.product();
                String finalFromWarehouse = request.fromWarehouse();
                String finalToWarehouse = request.toWarehouse();
                Integer finalQuantity = request.quantity();
                StringBuilder explanationBuilder = new StringBuilder();

                // 1. 如果审计服务可用，批量修正所有参数
                if (enhancedCorrectionService != null) {
                    try {
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
                        boolean needsConfirmation = false;

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
                                    logger.warn("参数{}修正失败，使用原始值", paramName);
                                    explanationBuilder.append(String.format("%s修正失败(使用原始值); ", paramName));
                                    break;
                            }
                        }

                        // 3. 处理需要确认的情况
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
                    } catch (Exception e) {
                        logger.error("参数修正服务异常，使用原始参数", e);
                        explanationBuilder.append("参数修正服务异常，使用原始参数; ");
                    }
                } else {
                    logger.debug("审计服务未启用，直接使用原始参数");
                    explanationBuilder.append("直接使用原始参数; ");
                }

                // 4. 执行库存调拨
                boolean transferSuccess = inventoryService.transferStock(
                        finalProduct, finalFromWarehouse, finalToWarehouse, finalQuantity
                );

                // 转换为ToolExecutionResult
                ToolExecutionResult transferResult = transferSuccess ?
                        ToolExecutionResult.success("调拨成功", "库存调拨已完成") :
                        ToolExecutionResult.error("调拨失败：库存不足或调拨异常");

                // 5. 增强结果说明
                String enhancedExplanation = transferResult.explain() + " | 参数处理: " + explanationBuilder.toString();

                // 6. 完成审计
                Duration executionTime = Duration.between(startTime, Instant.now());
                if (auditService != null) {
                    auditService.completeExecution(
                            executionId, transferResult.status(), transferResult.payload(), null,
                            Map.of("product", request.product(), "finalProduct", finalProduct,
                                    "fromWarehouse", finalFromWarehouse, "toWarehouse", finalToWarehouse,
                                    "quantity", finalQuantity),
                            executionTime.toMillis()
                    );
                    logger.info("审计记录已保存: executionId={}, executionTime={}ms", executionId, executionTime.toMillis());
                }

                // 7. 返回增强结果
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

                // 记录失败审计
                if (auditService != null) {
                    Duration executionTime = Duration.between(startTime, Instant.now());
                    auditService.completeExecution(
                            executionId, "error", null, e.getMessage(),
                            Map.of("product", request.product()),
                            executionTime.toMillis()
                    );
                }

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
                if (enhancedCorrectionService == null) {
                    return EnhancedToolExecutionResult.success(
                            Map.of("message", "审计服务未启用，无法追踪参数清洗过程"),
                            "审计服务未启用"
                    ).toJson();
                }

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

    // 增强版库存查询请求 - 兼容其他服务
    public record EnhancedStockQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称或ID。支持模糊匹配，如：'iPhone'、'苹果手机'、'P-001'等")
            String productName,

            @JsonPropertyDescription("仓库名称或区域。支持中文名称，如：'北京仓'、'华东区'、'上海'等")
            String warehouse,

            @JsonPropertyDescription("最小库存阈值。只返回库存大于此值的结果")
            Integer minStock,

            @JsonPropertyDescription("是否包含预留库存。可选，默认false")
            Boolean includeReserved,

            @JsonPropertyDescription("查询类型：'EXACT'(精确)、'FUZZY'(模糊)、'ALL'(全部)")
            String queryType
    ) {
    }

    // 批量库存查询请求
    public record BatchStockQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称列表。支持混合格式：['iPhone 15', 'P-002', '洗衣机']")
            String[] productNames,

            @JsonPropertyDescription("仓库筛选。为空则查询所有仓库")
            String warehouse,

            @JsonPropertyDescription("是否返回详细信息")
            Boolean detailed
    ) {
    }

    // 增强版调拨请求
    public record EnhancedTransferRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称或ID。支持模糊匹配")
            String productName,

            @JsonProperty(required = true)
            @JsonPropertyDescription("源仓库。支持中文名称，如：'北京仓'、'上海仓库'、'华东区'")
            String fromWarehouse,

            @JsonProperty(required = true)
            @JsonPropertyDescription("目标仓库。支持中文名称")
            String toWarehouse,

            @JsonProperty(required = true)
            @JsonPropertyDescription("调拨数量。支持中文数字，如：'一百'、'50台'、'全部'")
            String quantity,

            @JsonPropertyDescription("调拨原因或备注")
            String reason,

            @JsonPropertyDescription("优先级：'HIGH'(高)、'NORMAL'(普通)、'LOW'(低)")
            String priority,

            @JsonPropertyDescription("是否已确认。第一次调用请填false，用户确认后填true")
            Boolean confirmed
    ) {
    }

    // 库存预警查询请求
    public record StockAlertRequest(
            @JsonPropertyDescription("库存阈值。低于此值的产品会被返回")
            Integer threshold,

            @JsonPropertyDescription("产品类别筛选")
            String category,

            @JsonPropertyDescription("仓库筛选")
            String warehouse,

            @JsonPropertyDescription("是否只返回紧急情况（库存为0）")
            Boolean urgentOnly
    ) {
    }
}