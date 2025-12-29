package org.zerolg.aidemo2.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.zerolg.aidemo2.correction.annotation.ParameterCorrection;
import org.zerolg.aidemo2.correction.annotation.PositiveNumber;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.service.stock.EnhancedStockQueryService;
import org.zerolg.aidemo2.service.stock.EnhancedTransferService;

import jakarta.validation.constraints.*;

import java.util.function.Function;

/**
 * 增强版库存工具 - 集成参数修正系统
 * 与LLM Tools完美配合，提供智能参数处理
 */
@Configuration
public class EnhancedInventoryTools {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedInventoryTools.class);

    private final EnhancedStockQueryService stockQueryService;
    private final EnhancedTransferService transferService;

    public EnhancedInventoryTools(EnhancedStockQueryService stockQueryService,
                                  EnhancedTransferService transferService) {
        this.stockQueryService = stockQueryService;
        this.transferService = transferService;
    }

    // ========================================================================
    // LLM Tools - 智能库存查询
    // ========================================================================

    @Bean
    @Description("智能库存查询工具。支持模糊产品名称、自动纠错、多种查询条件。系统会自动标准化和验证参数")
    public Function<EnhancedStockQueryRequest, String> smartQueryStock() {
        return request -> {
            logger.info("🤖 LLM调用库存查询: {}", request);
            return stockQueryService.queryStock(request).toJson();
        };
    }

    @Bean
    @Description("批量库存查询工具。可以同时查询多个产品的库存信息")
    public Function<BatchStockQueryRequest, String> batchQueryStock() {
        return request -> {
            logger.info("🤖 LLM调用批量库存查询: {}", request);
            return stockQueryService.batchQuery(request).toJson();
        };
    }

    @Bean
    @Description("库存调拨工具。支持智能仓库名称识别和安全确认机制")
    public Function<EnhancedTransferRequest, String> smartTransferStock() {
        return request -> {
            logger.info("🤖 LLM调用库存调拨: {}", request);
            return transferService.executeTransfer(request).toJson();
        };
    }

    @Bean
    @Description("库存预警查询。查找库存低于指定阈值的产品")
    public Function<StockAlertRequest, String> queryLowStock() {
        return request -> {
            logger.info("🤖 LLM调用库存预警查询: {}", request);
            return stockQueryService.queryLowStock(request).toJson();
        };
    }

    // ========================================================================
    // 请求对象定义 - 支持参数修正
    // ========================================================================

    /**
     * 增强版库存查询请求
     * 支持多种查询条件和智能参数修正
     */
    public record EnhancedStockQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称或ID。支持模糊匹配，如：'iPhone'、'苹果手机'、'P-001'等")
            String productName,

            @JsonPropertyDescription("仓库名称或区域。支持中文名称，如：'北京仓'、'华东区'、'上海'等")
            String warehouse,

            @JsonPropertyDescription("最小库存阈值。只返回库存大于此值的结果")
            @PositiveNumber(includeZero = true)
            Integer minStock,

            @JsonPropertyDescription("是否包含预留库存。可选，默认false")
            Boolean includeReserved,

            @JsonPropertyDescription("查询类型：'EXACT'(精确)、'FUZZY'(模糊)、'ALL'(全部)")
            String queryType
    ) {
    }

    /**
     * 批量库存查询请求
     */
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

    /**
     * 增强版调拨请求
     */
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

    /**
     * 库存预警查询请求
     */
    public record StockAlertRequest(
            @JsonPropertyDescription("库存阈值。低于此值的产品会被返回")
            @PositiveNumber(includeZero = true)
            Integer threshold,

            @JsonPropertyDescription("产品类别筛选")
            String category,

            @JsonPropertyDescription("仓库筛选")
            String warehouse,

            @JsonPropertyDescription("是否只返回紧急情况（库存为0）")
            Boolean urgentOnly
    ) {
    }

    /**
     * 库存统计请求
     */
    public record StockStatisticsRequest(
            @JsonPropertyDescription("统计维度：'WAREHOUSE'(按仓库)、'CATEGORY'(按类别)、'PRODUCT'(按产品)")
            String dimension,

            @JsonPropertyDescription("时间范围：'TODAY'(今天)、'WEEK'(本周)、'MONTH'(本月)")
            String timeRange,

            @JsonPropertyDescription("是否包含历史趋势")
            Boolean includeTrend
    ) {
    }
}