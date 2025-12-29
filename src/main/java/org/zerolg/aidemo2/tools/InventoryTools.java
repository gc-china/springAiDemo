package org.zerolg.aidemo2.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.service.MockSearchService;
import org.zerolg.aidemo2.service.stock.EnhancedStockQueryService;
import org.zerolg.aidemo2.service.stock.EnhancedTransferService;

@Configuration
public class InventoryTools {

    private static final Logger logger = LoggerFactory.getLogger(InventoryTools.class);
    private final InventoryService inventoryService;
    private final MockSearchService searchService;
    private final EnhancedStockQueryService stockQueryService;
    private final EnhancedTransferService transferService;

    public InventoryTools(InventoryService inventoryService, MockSearchService searchService,
                          EnhancedStockQueryService stockQueryService, EnhancedTransferService transferService) {
        this.inventoryService = inventoryService;
        this.searchService = searchService;
        this.stockQueryService = stockQueryService;
        this.transferService = transferService;
    }

    // ========================================================================
    // 升级版工具 - 使用增强服务（集成参数修正系统）
    // ========================================================================

    @Bean
    @Description("智能库存查询工具。支持模糊名称查询，系统会自动矫正和标准化参数")
    public Function<StockQueryRequest, String> queryStock() {
        return request -> {
            // 使用增强版服务，自动进行参数修正
            var result = stockQueryService.queryStock(
                    request.product(), null, null, false, "FUZZY");
            return result.toJson();
        };
    }

    @Bean
    @Description("智能库存调拨工具。支持自然语言参数，自动安全确认。注意：只有在用户明确同意后才能调用此工具")
    public Function<TransferRequest, String> transferStock() {
        return request -> {
            // 使用增强版服务，自动进行参数修正和安全确认
            var result = transferService.executeTransfer(
                    request.product(),
                    request.fromWarehouse(),
                    request.toWarehouse(),
                    request.quantity().toString(),
                    "LLM调拨请求",
                    "NORMAL",
                    request.confirmed()
            );
            return result.toJson();
        };
    }

    // ========================================================================
    // 请求对象定义（保持兼容性）
    // ========================================================================

    public record TransferRequest(
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

            @JsonPropertyDescription("是否已确认。第一次调用请填 false，用户确认后填 true")
            Boolean confirmed
    ) {}

    public record StockQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称或ID。例如：'iPhone 15' 或 'P-001'")
            String product

    ) {}
}
