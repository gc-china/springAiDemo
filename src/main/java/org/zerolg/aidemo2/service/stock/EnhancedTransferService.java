package org.zerolg.aidemo2.service.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.common.ToolExecutionResult;
import org.zerolg.aidemo2.correction.annotation.ParameterCorrection;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.service.MockSearchService;
import org.zerolg.aidemo2.tools.EnhancedInventoryTools.EnhancedTransferRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 增强版库存调拨服务
 * 集成参数修正系统，提供智能调拨功能
 */
@Service
public class EnhancedTransferService {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedTransferService.class);
    // 中文数字转换模式
    private static final Pattern CHINESE_NUMBER_PATTERN = Pattern.compile(".*[一二三四五六七八九十百千万].*");
    private static final Pattern ALL_PATTERN = Pattern.compile(".*全部|所有|全量.*", Pattern.CASE_INSENSITIVE);
    private final InventoryService inventoryService;
    private final MockSearchService searchService;

    public EnhancedTransferService(InventoryService inventoryService, MockSearchService searchService) {
        this.inventoryService = inventoryService;
        this.searchService = searchService;
    }

    /**
     * 智能库存调拨 - 支持参数自动修正和安全确认
     */
    @ParameterCorrection(
            failOnError = false,
            autoConfirm = true,
            minConfidence = 0.7,
            logFailures = true
    )
    public ToolExecutionResult executeTransfer(
            @NotBlank String productName,
            @NotBlank String fromWarehouse,
            @NotBlank String toWarehouse,
            @NotNull String quantity,
            String reason,
            String priority,
            Boolean confirmed) {

        logger.info("🚚 执行智能库存调拨: product={}, from={}, to={}, quantity={}, confirmed={}",
                productName, fromWarehouse, toWarehouse, quantity, confirmed);

        try {
            // 1. 解析产品ID（参数修正系统已处理产品名称）
            String productId = resolveProductId(productName);
            if (productId == null) {
                return ToolExecutionResult.notFound("未找到产品: " + productName);
            }

            // 2. 标准化仓库名称
            String standardFromWarehouse = standardizeWarehouse(fromWarehouse);
            String standardToWarehouse = standardizeWarehouse(toWarehouse);

            // 3. 解析调拨数量（参数修正系统已处理数量格式）
            Integer transferQuantity = parseQuantity(quantity, productId, standardFromWarehouse);
            if (transferQuantity == null || transferQuantity <= 0) {
                return ToolExecutionResult.error("无效的调拨数量: " + quantity);
            }

            // 4. 安全检查和确认机制
            if (!Boolean.TRUE.equals(confirmed)) {
                return performSafetyCheck(productId, standardFromWarehouse, standardToWarehouse,
                        transferQuantity, reason, priority);
            }

            // 5. 执行调拨
            return performTransfer(productId, standardFromWarehouse, standardToWarehouse,
                    transferQuantity, reason, priority);

        } catch (Exception e) {
            logger.error("库存调拨异常", e);
            return ToolExecutionResult.error("调拨失败: " + e.getMessage());
        }
    }

    /**
     * 重载方法 - 接受请求对象
     */
    public ToolExecutionResult executeTransfer(EnhancedTransferRequest request) {
        return executeTransfer(
                request.productName(),
                request.fromWarehouse(),
                request.toWarehouse(),
                request.quantity(),
                request.reason(),
                request.priority(),
                request.confirmed()
        );
    }

    // ========================================================================
    // 核心业务逻辑
    // ========================================================================

    /**
     * 安全检查和确认
     */
    private ToolExecutionResult performSafetyCheck(String productId, String fromWarehouse,
                                                   String toWarehouse, Integer quantity, String reason, String priority) {

        try {
            // 检查源仓库库存
            int currentStock = inventoryService.getStock(productId);
            String productName = inventoryService.getProductName(productId);

            // 风险评估
            RiskLevel riskLevel = assessRisk(currentStock, quantity, fromWarehouse, toWarehouse);

            // 构建确认信息
            TransferConfirmation confirmation = new TransferConfirmation(
                    productId,
                    productName,
                    fromWarehouse,
                    toWarehouse,
                    quantity,
                    currentStock,
                    riskLevel,
                    reason,
                    priority,
                    LocalDateTime.now()
            );

            String message = buildConfirmationMessage(confirmation);

            logger.info("⚠️ 调拨需要确认: {}", confirmation);

            return ToolExecutionResult.needsConfirmation(confirmation, message);

        } catch (Exception e) {
            logger.error("安全检查失败", e);
            return ToolExecutionResult.error("安全检查失败: " + e.getMessage());
        }
    }

    /**
     * 执行调拨操作
     */
    private ToolExecutionResult performTransfer(String productId, String fromWarehouse,
                                                String toWarehouse, Integer quantity, String reason, String priority) {

        try {
            // 再次检查库存（防止并发问题）
            int currentStock = inventoryService.getStock(productId);
            if (currentStock < quantity) {
                return ToolExecutionResult.error(
                        String.format("库存不足：当前库存 %d，需要调拨 %d", currentStock, quantity));
            }

            // 执行调拨（这里是模拟实现）
            boolean success = inventoryService.transferStock(productId, fromWarehouse, toWarehouse, quantity);

            if (success) {
                TransferResult result = new TransferResult(
                        generateTransferId(),
                        productId,
                        inventoryService.getProductName(productId),
                        fromWarehouse,
                        toWarehouse,
                        quantity,
                        "SUCCESS",
                        reason,
                        priority,
                        LocalDateTime.now()
                );

                logger.info("✅ 调拨成功: {}", result);

                return ToolExecutionResult.success(result,
                        String.format("调拨成功：%s 从 %s 调拨到 %s，数量 %d",
                                result.productName(), fromWarehouse, toWarehouse, quantity));
            } else {
                return ToolExecutionResult.error("调拨操作失败，请稍后重试");
            }

        } catch (Exception e) {
            logger.error("执行调拨失败", e);
            return ToolExecutionResult.error("调拨执行失败: " + e.getMessage());
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 解析产品ID
     */
    private String resolveProductId(String productName) {
        if (productName.startsWith("P-")) {
            return productName;
        }

        var searchResults = searchService.fuzzySearch(productName);
        return searchResults.isEmpty() ? null : searchResults.get(0).id();
    }

    /**
     * 标准化仓库名称
     */
    private String standardizeWarehouse(String warehouse) {
        if (warehouse == null || warehouse.trim().isEmpty()) {
            return "UNKNOWN";
        }

        String normalized = warehouse.trim();

        // 标准化常见仓库名称 - 使用HashMap避免Map.of()限制
        Map<String, String> warehouseMapping = new HashMap<>();
        warehouseMapping.put("北京", "BEIJING_WH");
        warehouseMapping.put("北京仓", "BEIJING_WH");
        warehouseMapping.put("北京仓库", "BEIJING_WH");
        warehouseMapping.put("上海", "SHANGHAI_WH");
        warehouseMapping.put("上海仓", "SHANGHAI_WH");
        warehouseMapping.put("上海仓库", "SHANGHAI_WH");
        warehouseMapping.put("广州", "GUANGZHOU_WH");
        warehouseMapping.put("深圳", "SHENZHEN_WH");
        warehouseMapping.put("华东", "EAST_REGION");
        warehouseMapping.put("华北", "NORTH_REGION");
        warehouseMapping.put("华南", "SOUTH_REGION");
        warehouseMapping.put("华西", "WEST_REGION");

        return warehouseMapping.getOrDefault(normalized, normalized.toUpperCase().replace(" ", "_"));
    }

    /**
     * 解析调拨数量（参数修正系统已经处理了中文数字）
     */
    private Integer parseQuantity(String quantityStr, String productId, String fromWarehouse) {
        if (quantityStr == null || quantityStr.trim().isEmpty()) {
            return null;
        }

        String cleaned = quantityStr.trim().toLowerCase();

        // 处理"全部"、"所有"等关键词
        if (ALL_PATTERN.matcher(cleaned).matches()) {
            try {
                return inventoryService.getStock(productId);
            } catch (Exception e) {
                logger.warn("获取全部库存失败: {}", productId, e);
                return null;
            }
        }

        // 移除单位词
        cleaned = cleaned.replaceAll("[台个件箱批]", "");

        // 尝试解析数字
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            logger.warn("无法解析数量: {}", quantityStr);
            return null;
        }
    }

    /**
     * 风险评估
     */
    private RiskLevel assessRisk(int currentStock, int transferQuantity,
                                 String fromWarehouse, String toWarehouse) {

        double transferRatio = (double) transferQuantity / currentStock;

        if (transferQuantity == currentStock) {
            return RiskLevel.HIGH; // 全部调拨
        } else if (transferRatio > 0.8) {
            return RiskLevel.MEDIUM; // 大部分调拨
        } else if (transferQuantity > 1000) {
            return RiskLevel.MEDIUM; // 大数量调拨
        } else {
            return RiskLevel.LOW; // 正常调拨
        }
    }

    /**
     * 构建确认消息
     */
    private String buildConfirmationMessage(TransferConfirmation confirmation) {
        StringBuilder message = new StringBuilder();

        message.append("🚨 库存调拨确认\n\n");
        message.append("📦 产品：").append(confirmation.productName()).append("\n");
        message.append("📍 从：").append(confirmation.fromWarehouse()).append("\n");
        message.append("📍 到：").append(confirmation.toWarehouse()).append("\n");
        message.append("📊 数量：").append(confirmation.quantity()).append(" / ").append(confirmation.currentStock()).append("\n");

        if (confirmation.reason() != null) {
            message.append("📝 原因：").append(confirmation.reason()).append("\n");
        }

        message.append("⚠️ 风险等级：").append(confirmation.riskLevel()).append("\n\n");

        switch (confirmation.riskLevel()) {
            case HIGH -> message.append("⚠️ 高风险操作：将调拨大部分或全部库存，请谨慎确认！");
            case MEDIUM -> message.append("⚠️ 中风险操作：调拨数量较大，请确认操作无误。");
            case LOW -> message.append("✅ 低风险操作：正常调拨操作。");
        }

        message.append("\n\n请确认是否继续执行调拨操作？");

        return message.toString();
    }

    /**
     * 生成调拨ID
     */
    private String generateTransferId() {
        return "TF-" + System.currentTimeMillis();
    }

    // ========================================================================
    // 数据对象定义
    // ========================================================================

    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }

    public record TransferConfirmation(
            String productId,
            String productName,
            String fromWarehouse,
            String toWarehouse,
            Integer quantity,
            Integer currentStock,
            RiskLevel riskLevel,
            String reason,
            String priority,
            LocalDateTime requestTime
    ) {
    }

    public record TransferResult(
            String transferId,
            String productId,
            String productName,
            String fromWarehouse,
            String toWarehouse,
            Integer quantity,
            String status,
            String reason,
            String priority,
            LocalDateTime completedTime
    ) {
    }
}