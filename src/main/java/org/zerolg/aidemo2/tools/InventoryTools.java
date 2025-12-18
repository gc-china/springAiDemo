package org.zerolg.aidemo2.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.service.MockSearchService;
import org.zerolg.aidemo2.service.MockSearchService.SearchResult;
import org.zerolg.aidemo2.service.TransferToolService;

@Configuration
public class InventoryTools {

    private static final Logger logger = LoggerFactory.getLogger(InventoryTools.class);
    private final InventoryService inventoryService;
    private final MockSearchService searchService;
    private final TransferToolService transferToolService;

    public InventoryTools(InventoryService inventoryService, MockSearchService searchService,
                          TransferToolService transferToolService) {
        this.inventoryService = inventoryService;
        this.searchService = searchService;
        this.transferToolService = transferToolService;
    }

    // ========================================================================
    // 方案四：查询工具 (配合 AOP 切面使用)
    // ========================================================================

    public record StockQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称或ID。例如：'iPhone 15' 或 'P-001'")
            String product
    ) {}

    @Bean
    @Description("查询库存数量。支持模糊名称查询，系统会自动矫正")
    public Function<StockQueryRequest, String> queryStock() {
        return request -> {
            // 注意：如果 AOP 工作正常，这里的 product 应该已经被替换为 ID 了
            String rawName = request.product();
            // 简单的判断：如果是 P- 开头，说明是 ID
            if (rawName.startsWith("P-")) {
                int stock = inventoryService.getStock(rawName);
                return "产品ID [" + rawName + "] 的当前库存为: " + stock;
            } else {

                logger.info("🛑 拦截到模糊参数: [{}],正在进行搜索引擎矫正...", rawName);

                // 2. 调用搜索引擎
                List<SearchResult> matches = searchService.fuzzySearch(rawName);

                // 3. 决策逻辑
                if (matches.size() == 1) {
                    // ✅ 情况A: 唯一匹配 -> 自动矫正
                    SearchResult match = matches.get(0);
                    logger.info("✅ 找到唯一匹配: {} -> {} ({})", rawName, match.name(), match.id());

                    String correctedId = match.id();
                    int stock = inventoryService.getStock(correctedId);
                    return "产品ID [" + rawName + "] 的当前库存为: " + stock;
                } else if (matches.size() > 1) {
                    return String.format(
                            "错误：参数 '%s' 存在歧义，无法执行查询。可能有以下产品：%s。\n" +
                                    "请注意：**不要再次尝试使用相同的参数调用工具**。\n" +
                                    "请直接回复用户：'找到多个相关产品，请问您是指哪一个？' 并列出候选项。",
                            rawName, matches
                    );

                } else {
                    // ❌ 情况C: 无匹配 -> 返回错误
                    logger.warn("❌ 未找到匹配: {}", rawName);
                    return "未找到名称包含 '" + rawName + "' 的产品。请检查名称是否正确。";
                }

            }
        };
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

    @Bean
    @Description("用于执行库存调拨。注意：只有在用户明确同意后才能调用此工具。调用后，请直接向用户报告成功或失败的具体原因，不要再次请求确认")
    public Function<TransferRequest, String> transferStock() {
        return transferToolService::executeTransfer;
    }
}
