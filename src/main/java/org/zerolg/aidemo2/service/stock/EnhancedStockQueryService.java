package org.zerolg.aidemo2.service.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.common.ToolExecutionResult;
import org.zerolg.aidemo2.correction.annotation.ParameterCorrection;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.service.MockSearchService;
import org.zerolg.aidemo2.tools.IntegratedInventoryTools.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强版库存查询服务
 * 集成参数修正系统，提供智能查询功能
 */
@Service
public class EnhancedStockQueryService {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedStockQueryService.class);

    private final InventoryService inventoryService;
    private final MockSearchService searchService;

    public EnhancedStockQueryService(InventoryService inventoryService, MockSearchService searchService) {
        this.inventoryService = inventoryService;
        this.searchService = searchService;
    }

    /**
     * 智能库存查询 - 支持参数自动修正
     */
    @ParameterCorrection(
            failOnError = false,
            autoConfirm = true,
            minConfidence = 0.6,
            logFailures = true
    )
    public ToolExecutionResult queryStock(
            @NotBlank String productName,
            String warehouse,
            Integer minStock,
            Boolean includeReserved,
            String queryType) {

        logger.info("📦 执行智能库存查询: product={}, warehouse={}, minStock={}",
                productName, warehouse, minStock);

        try {
            // 1. 产品名称已经被参数修正系统处理过了
            List<String> productIds = resolveProductIds(productName, queryType);

            if (productIds.isEmpty()) {
                return ToolExecutionResult.notFound("未找到匹配的产品: " + productName);
            }

            // 2. 仓库名称标准化
            String standardWarehouse = standardizeWarehouse(warehouse);

            // 3. 查询库存
            List<StockInfo> results = new ArrayList<>();
            for (String productId : productIds) {
                try {
                    int stock = inventoryService.getStock(productId);

                    // 应用最小库存过滤
                    if (minStock != null && stock < minStock) {
                        continue;
                    }

                    // 处理预留库存
                    if (Boolean.TRUE.equals(includeReserved)) {
                        stock += inventoryService.getReservedStock(productId);
                    }

                    results.add(new StockInfo(productId, getProductName(productId),
                            standardWarehouse, stock, includeReserved));

                } catch (Exception e) {
                    logger.warn("查询产品库存失败: {}", productId, e);
                }
            }

            if (results.isEmpty()) {
                return ToolExecutionResult.success(Collections.emptyList(),
                        "查询完成，但没有符合条件的库存记录");
            }

            return ToolExecutionResult.success(results,
                    String.format("成功查询到 %d 个产品的库存信息", results.size()));

        } catch (Exception e) {
            logger.error("库存查询异常", e);
            return ToolExecutionResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 重载方法 - 接受请求对象
     */
    public ToolExecutionResult queryStock(EnhancedStockQueryRequest request) {
        return queryStock(
                request.productName(),
                request.warehouse(),
                request.minStock(),
                request.includeReserved(),
                request.queryType()
        );
    }

    /**
     * 批量库存查询
     */
    @ParameterCorrection(
            mode = ParameterCorrection.CorrectionMode.NORMALIZE_AND_VALIDATE,
            minConfidence = 0.5
    )
    public ToolExecutionResult batchQuery(BatchStockQueryRequest request) {
        logger.info("📦 执行批量库存查询: products={}", Arrays.toString(request.productNames()));

        try {
            List<BatchStockResult> results = new ArrayList<>();

            for (String productName : request.productNames()) {
                try {
                    // 每个产品名称都会被参数修正系统处理
                    ToolExecutionResult singleResult = queryStock(productName,
                            request.warehouse(), null, false, "FUZZY");

                    if (singleResult.isSuccess()) {
                        @SuppressWarnings("unchecked")
                        List<StockInfo> stockInfos = (List<StockInfo>) singleResult.getPayload();
                        results.add(new BatchStockResult(productName, "SUCCESS", stockInfos));
                    } else {
                        results.add(new BatchStockResult(productName, "NOT_FOUND", Collections.emptyList()));
                    }

                } catch (Exception e) {
                    logger.warn("批量查询中单个产品失败: {}", productName, e);
                    results.add(new BatchStockResult(productName, "ERROR", Collections.emptyList()));
                }
            }

            return ToolExecutionResult.success(results,
                    String.format("批量查询完成，处理了 %d 个产品", request.productNames().length));

        } catch (Exception e) {
            logger.error("批量库存查询异常", e);
            return ToolExecutionResult.error("批量查询失败: " + e.getMessage());
        }
    }

    /**
     * 库存预警查询
     */
    @ParameterCorrection
    public ToolExecutionResult queryLowStock(StockAlertRequest request) {
        logger.info("⚠️ 执行库存预警查询: threshold={}, category={}",
                request.threshold(), request.category());

        try {
            int threshold = request.threshold() != null ? request.threshold() : 10;
            List<StockAlert> alerts = new ArrayList<>();

            // 模拟查询所有产品的库存
            List<String> allProductIds = inventoryService.getAllProductIds();

            for (String productId : allProductIds) {
                try {
                    int stock = inventoryService.getStock(productId);

                    // 检查是否低于阈值
                    if (stock <= threshold) {
                        // 紧急情况过滤
                        if (Boolean.TRUE.equals(request.urgentOnly()) && stock > 0) {
                            continue;
                        }

                        String alertLevel = stock == 0 ? "CRITICAL" :
                                stock <= threshold / 2 ? "HIGH" : "MEDIUM";

                        alerts.add(new StockAlert(productId, getProductName(productId),
                                stock, threshold, alertLevel));
                    }

                } catch (Exception e) {
                    logger.warn("检查产品库存预警失败: {}", productId, e);
                }
            }

            alerts.sort(Comparator.comparing(StockAlert::currentStock));

            return ToolExecutionResult.success(alerts,
                    String.format("发现 %d 个库存预警项目", alerts.size()));

        } catch (Exception e) {
            logger.error("库存预警查询异常", e);
            return ToolExecutionResult.error("预警查询失败: " + e.getMessage());
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 解析产品ID列表
     */
    private List<String> resolveProductIds(String productName, String queryType) {
        // 如果已经是标准ID格式
        if (productName.startsWith("P-")) {
            return List.of(productName);
        }

        // 使用搜索服务进行模糊匹配
        var searchResults = searchService.fuzzySearch(productName);

        if ("EXACT".equals(queryType)) {
            // 精确匹配模式
            return searchResults.stream()
                    .filter(r -> r.name().equalsIgnoreCase(productName))
                    .map(MockSearchService.SearchResult::id)
                    .collect(Collectors.toList());
        } else {
            // 模糊匹配模式（默认）
            return searchResults.stream()
                    .map(MockSearchService.SearchResult::id)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 标准化仓库名称
     */
    private String standardizeWarehouse(String warehouse) {
        if (warehouse == null || warehouse.trim().isEmpty()) {
            return "ALL";
        }

        String normalized = warehouse.trim();

        // 标准化常见仓库名称
        Map<String, String> warehouseMapping = Map.of(
                "北京", "BEIJING_WH",
                "上海", "SHANGHAI_WH",
                "广州", "GUANGZHOU_WH",
                "深圳", "SHENZHEN_WH",
                "华东", "EAST_REGION",
                "华北", "NORTH_REGION",
                "华南", "SOUTH_REGION"
        );

        return warehouseMapping.getOrDefault(normalized, normalized.toUpperCase());
    }

    /**
     * 获取产品名称
     */
    private String getProductName(String productId) {
        try {
            return inventoryService.getProductName(productId);
        } catch (Exception e) {
            return productId; // 降级返回ID
        }
    }

    // ========================================================================
    // 结果对象定义
    // ========================================================================

    public record StockInfo(
            String productId,
            String productName,
            String warehouse,
            int currentStock,
            Boolean includeReserved
    ) {
    }

    public record BatchStockResult(
            String productName,
            String status,
            List<StockInfo> stockInfos
    ) {
    }

    public record StockAlert(
            String productId,
            String productName,
            int currentStock,
            int threshold,
            String alertLevel
    ) {
    }
}