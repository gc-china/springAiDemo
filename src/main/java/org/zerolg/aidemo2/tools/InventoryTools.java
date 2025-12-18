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
import org.zerolg.aidemo2.service.stock.StockQueryService;
import org.zerolg.aidemo2.service.stock.TransferToolService;

@Configuration
public class InventoryTools {

    private static final Logger logger = LoggerFactory.getLogger(InventoryTools.class);
    private final InventoryService inventoryService;
    private final MockSearchService searchService;
    private final TransferToolService transferToolService;
    private final StockQueryService stockQueryService;

    public InventoryTools(InventoryService inventoryService, MockSearchService searchService,
                          TransferToolService transferToolService, StockQueryService stockQueryService) {
        this.inventoryService = inventoryService;
        this.searchService = searchService;
        this.stockQueryService = stockQueryService;
        this.transferToolService = transferToolService;
    }

    // ========================================================================
    // 方案四：查询工具 (配合 AOP 切面使用)
    // ========================================================================

    @Bean
    @Description("查询库存数量。支持模糊名称查询，系统会自动矫正")
    public Function<StockQueryRequest, String> queryStock() {
        return request -> stockQueryService.queryStock(request).toJson();
    }

    @Bean
    @Description("用于执行库存调拨。注意：只有在用户明确同意后才能调用此工具。调用后，请直接向用户报告成功或失败的具体原因，不要再次请求确认")
    public Function<TransferRequest, String> transferStock() {
        return request -> transferToolService.executeTransfer(request).toJson();
    }

    // ========================================================================
    // 方案三：调拨工具 (内置人机确认逻辑)
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
