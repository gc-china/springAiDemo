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

@Configuration
public class InventoryTools {

    private static final Logger logger = LoggerFactory.getLogger(InventoryTools.class);
    private final InventoryService inventoryService;
    private final MockSearchService searchService;

    public InventoryTools(InventoryService inventoryService,MockSearchService searchService) {
        this.inventoryService = inventoryService;
        this.searchService = searchService;
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
                    inventoryService.getStock(correctedId);
                } else if (matches.size() > 1) {
                    // ❓ 情况B: 多个匹配 -> 返回歧义提示
                    String names = matches.stream()
                            .map(SearchResult::name)
                            .collect(Collectors.joining(", "));
                    logger.warn("❓ 发现歧义: {} -> [{}]", rawName, names);

                    return "找到多个相关产品: " + names + "。请问您具体是指哪一个？";

                } else {
                    // ❌ 情况C: 无匹配 -> 返回错误
                    logger.warn("❌ 未找到匹配: {}", rawName);
                    return "未找到名称包含 '" + rawName + "' 的产品。请检查名称是否正确。";
                }

                // 如果还是名称，说明 AOP 没拦截或者没找到，这里做兜底
                return "未找到产品 [" + rawName + "]，请尝试提供更准确的名称。";
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
    @Description("调拨库存。这是一个敏感操作，需要用户确认")
    public Function<TransferRequest, String> transferStock() {
        return request -> {
            boolean isConfirmed = request.confirmed() != null && request.confirmed();

            if (!isConfirmed) {
                // 🛑 阶段一：返回确认单
                logger.info("收到调拨请求，等待确认: {}", request);
                return String.format("""
                        ⚠️ **操作确认**
                        您申请将 %d 个 [%s] 从 %s 调拨到 %s。
                        请回复“确认”以执行此操作，或回复“取消”以撤销。
                        """,
                        request.quantity(), request.product(), request.fromWarehouse(), request.toWarehouse());
            } else {
                // ✅ 阶段二：执行操作
                try {
                    // 这里简化处理，假设 product 已经是 ID 或者名称 (生产环境这里也可以结合 AOP 矫正)
                    inventoryService.transferStock(request.product(), request.fromWarehouse(), request.toWarehouse(), request.quantity());
                    return "✅ 调拨执行成功！";
                } catch (Exception e) {
                    return "❌ 执行失败: " + e.getMessage();
                }
            }
        };
    }
}
