package org.zerolg.aidemo2.example;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import org.zerolg.aidemo2.correction.annotation.ParameterCorrection;
import org.zerolg.aidemo2.correction.annotation.PositiveNumber;
import org.zerolg.aidemo2.service.stock.EnhancedStockQueryService;
import org.zerolg.aidemo2.service.stock.EnhancedTransferService;
import org.zerolg.aidemo2.tools.EnhancedInventoryTools.*;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM Tools集成示例
 * 展示参数修正系统如何与LLM Tools完美配合
 */
@RestController
@RequestMapping("/api/llm-tools")
public class LLMToolsIntegrationExample {

    private final EnhancedStockQueryService stockQueryService;
    private final EnhancedTransferService transferService;

    public LLMToolsIntegrationExample(EnhancedStockQueryService stockQueryService,
                                      EnhancedTransferService transferService) {
        this.stockQueryService = stockQueryService;
        this.transferService = transferService;
    }

    /**
     * 模拟LLM调用库存查询工具
     * 展示参数修正如何处理LLM传递的各种格式参数
     */
    @PostMapping("/query-stock")
    @ParameterCorrection(
            failOnError = false,
            autoConfirm = true,
            minConfidence = 0.6,
            logFailures = true
    )
    public Map<String, Object> simulateLLMStockQuery(
            @RequestParam @NotBlank String productName,    // LLM可能传递: "苹果手机"、"iPhone 15"、"P-001"
            @RequestParam(required = false) String warehouse,  // LLM可能传递: "北京仓"、"上海"、"华东区"
            @RequestParam(required = false) @PositiveNumber(includeZero = true) Integer minStock) { // LLM可能传递: "十个"、"50"

        Map<String, Object> response = new HashMap<>();
        response.put("llm_input", Map.of(
                "productName", productName,
                "warehouse", warehouse,
                "minStock", minStock
        ));

        // 调用增强版库存查询服务（参数已被修正）
        var result = stockQueryService.queryStock(productName, warehouse, minStock, false, "FUZZY");

        response.put("query_result", result);
        response.put("message", "LLM库存查询完成，参数已自动修正");

        return response;
    }

    /**
     * 模拟LLM调用库存调拨工具
     */
    @PostMapping("/transfer-stock")
    @ParameterCorrection(
            failOnError = false,
            autoConfirm = false,  // 调拨操作需要用户确认
            minConfidence = 0.7
    )
    public Map<String, Object> simulateLLMStockTransfer(
            @RequestParam @NotBlank String productName,     // "iPhone 15"、"苹果手机"
            @RequestParam @NotBlank String fromWarehouse,   // "北京仓"、"上海"
            @RequestParam @NotBlank String toWarehouse,     // "广州仓库"、"深圳"
            @RequestParam @NotBlank String quantity,        // "五十台"、"全部"、"100"
            @RequestParam(required = false) String reason,  // "补货"、"调配库存"
            @RequestParam(defaultValue = "false") Boolean confirmed) {

        Map<String, Object> response = new HashMap<>();
        response.put("llm_input", Map.of(
                "productName", productName,
                "fromWarehouse", fromWarehouse,
                "toWarehouse", toWarehouse,
                "quantity", quantity,
                "reason", reason,
                "confirmed", confirmed
        ));

        // 调用增强版调拨服务（参数已被修正）
        var result = transferService.executeTransfer(productName, fromWarehouse, toWarehouse,
                quantity, reason, "NORMAL", confirmed);

        response.put("transfer_result", result);
        response.put("message", "LLM库存调拨处理完成");

        return response;
    }

    /**
     * 批量操作示例 - LLM传递多个产品
     */
    @PostMapping("/batch-query")
    @ParameterCorrection(
            mode = ParameterCorrection.CorrectionMode.NORMALIZE_AND_VALIDATE
    )
    public Map<String, Object> simulateLLMBatchQuery(
            @RequestParam String[] productNames,  // ["苹果手机", "华为P50", "小米13"]
            @RequestParam(required = false) String warehouse) {

        Map<String, Object> response = new HashMap<>();
        response.put("llm_input", Map.of(
                "productNames", productNames,
                "warehouse", warehouse
        ));

        // 创建批量查询请求
        BatchStockQueryRequest request = new BatchStockQueryRequest(productNames, warehouse, true);
        var result = stockQueryService.batchQuery(request);

        response.put("batch_result", result);
        response.put("message", "LLM批量查询完成");

        return response;
    }

    /**
     * 智能搜索示例 - 处理模糊查询
     */
    @GetMapping("/smart-search")
    @ParameterCorrection(
            includeParameters = {"keyword"}, // 只修正关键词参数
            minConfidence = 0.5
    )
    public Map<String, Object> simulateLLMSmartSearch(
            @RequestParam String keyword,                    // "手机"、"洗衣机"、"电视"
            @RequestParam(required = false) String category, // "电子产品"、"家电"
            @RequestParam(defaultValue = "10") Integer limit) {

        Map<String, Object> response = new HashMap<>();
        response.put("llm_input", Map.of(
                "keyword", keyword,
                "category", category,
                "limit", limit
        ));

        // 模拟智能搜索逻辑
        EnhancedStockQueryRequest searchRequest = new EnhancedStockQueryRequest(
                keyword, null, null, false, "FUZZY");

        var result = stockQueryService.queryStock(searchRequest);

        response.put("search_result", result);
        response.put("message", "LLM智能搜索完成");

        return response;
    }

    /**
     * 复杂场景示例 - 多步骤操作
     */
    @PostMapping("/complex-operation")
    @ParameterCorrection(
            failOnError = false,
            autoConfirm = true,
            interactiveMode = true  // 启用交互模式
    )
    public Map<String, Object> simulateComplexLLMOperation(
            @RequestParam String operation,      // "查询并调拨"、"批量检查"
            @RequestParam String[] products,     // 产品列表
            @RequestParam String sourceWarehouse,
            @RequestParam String targetWarehouse,
            @RequestParam(required = false) String condition) { // "库存低于50"

        Map<String, Object> response = new HashMap<>();
        response.put("llm_input", Map.of(
                "operation", operation,
                "products", products,
                "sourceWarehouse", sourceWarehouse,
                "targetWarehouse", targetWarehouse,
                "condition", condition
        ));

        // 模拟复杂操作逻辑
        response.put("steps", new String[]{
                "1. 参数修正完成",
                "2. 产品名称标准化",
                "3. 仓库名称映射",
                "4. 条件解析",
                "5. 操作执行"
        });

        response.put("message", "复杂LLM操作处理完成");

        return response;
    }

    /**
     * 获取参数修正统计 - 用于LLM调试
     */
    @GetMapping("/correction-stats")
    public Map<String, Object> getCorrectionStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("normalizers", "字符串、数值、日期、LLM专用");
        stats.put("validators", "类型、范围、业务规则");
        stats.put("resolvers", "实体解析、状态映射");
        stats.put("features", new String[]{
                "智能产品名称识别",
                "仓库名称标准化",
                "中文数字转换",
                "模糊匹配",
                "安全确认机制"
        });

        return stats;
    }
}