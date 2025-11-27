package org.zerolg.aidemo2.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.Map;
import java.util.function.Function;

/**
 * 完整示例：展示如何处理 ID vs 名称 的问题
 */
@Configuration
public class ProductTools {

    private static final Logger logger = LoggerFactory.getLogger(ProductTools.class);

    // 模拟的名称到ID映射
    private static final Map<String, String> NAME_TO_ID = Map.of(
            "马桶", "PROD-001",
            "洗脸盆", "PROD-002",
            "淋浴头", "PROD-003"
    );

    // ========== 方案A: 两步工具调用 ==========

    // 步骤1: 根据名称查询ID
    public record ProductNameRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品名称，例如：马桶、洗脸盆")
            String productName
    ) {}

    public record ProductIdResult(
            String productId,
            String productName
    ) {}

    @Bean
    @Description("根据产品名称查询产品ID。当用户提到产品名称但你需要产品ID时使用")
    public Function<ProductNameRequest, ProductIdResult> findProductId() {
        return request -> {
            logger.info("🔧 查询产品ID: {}", request.productName());
            String id = NAME_TO_ID.get(request.productName());
            if (id == null) {
                return new ProductIdResult(null, request.productName());
            }
            return new ProductIdResult(id, request.productName());
        };
    }

    // 步骤2: 根据ID查询库存
    public record ProductIdRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("产品ID，格式为 PROD-XXX，例如：PROD-001")
            String productId
    ) {}

    @Bean
    @Description("根据产品ID查询库存数量")
    public Function<ProductIdRequest, Integer> queryStockById() {
        return request -> {
            logger.info("🔧 查询库存: {}", request.productId());
            // 模拟库存数据
            return switch (request.productId()) {
                case "PROD-001" -> 75;
                case "PROD-002" -> 120;
                case "PROD-003" -> 50;
                default -> 0;
            };
        };
    }

    // ========== 方案B: 支持多种输入方式 ==========

    public record FlexibleProductRequest(
            @JsonPropertyDescription("商品ID，例如：PROD-001。如果不知道ID，可以不填")
            String productId,

            @JsonPropertyDescription("商品名称，例如：马桶。如果已知ID，可以不填。ID和名称至少提供一个")
            String productName
    ) {}

    @Bean
    @Description("灵活的库存查询。可以通过商品ID或商品名称查询，系统会自动处理转换")
    public Function<FlexibleProductRequest, Integer> queryStockFlexible() {
        return request -> {
            String id = request.productId();
            String name = request.productName();

            logger.info("🔧 灵活查询: ID={}, Name={}", id, name);

            // 如果只有名称，先转换为ID
            if (id == null && name != null) {
                id = NAME_TO_ID.get(name);
                logger.info("   名称 '{}' 转换为 ID '{}'", name, id);
            }

            if (id == null) {
                logger.warn("   无法确定产品ID");
                return 0;
            }

            // 查询库存
            return switch (id) {
                case "PROD-001" -> 75;
                case "PROD-002" -> 120;
                case "PROD-003" -> 50;
                default -> 0;
            };
        };
    }

    // ========== 方案C: 复杂多条件查询 ==========

    public record AdvancedStockQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("商品名称或ID，必填。例如：'马桶' 或 'PROD-001'")
            String product,

            @JsonPropertyDescription("仓库区域，可选。例如：'华东'、'华北'、'华南'。不填则查询所有区域")
            String region,

            @JsonPropertyDescription("库存阈值，只返回库存大于此值的结果。可选，默认为0")
            Integer minStock,

            @JsonPropertyDescription("是否包含预留库存。可选，默认false")
            Boolean includeReserved
    ) {}

    public record StockResult(
            String productId,
            String productName,
            String region,
            int availableStock,
            int reservedStock,
            int totalStock
    ) {}

    @Bean
    @Description("高级库存查询。支持按区域、库存阈值等多条件筛选。可以处理复杂的查询需求")
    public Function<AdvancedStockQueryRequest, StockResult> advancedStockQuery() {
        return request -> {
            logger.info("🔧 高级查询: {}", request);

            // 1. 处理产品ID/名称
            String id = request.product();
            String name = request.product();
            if (NAME_TO_ID.containsKey(request.product())) {
                id = NAME_TO_ID.get(request.product());
            }

            // 2. 处理可选参数
            String region = request.region() != null ? request.region() : "全国";
            int minStock = request.minStock() != null ? request.minStock() : 0;
            boolean includeReserved = request.includeReserved() != null ? request.includeReserved() : false;

            // 3. 模拟复杂查询逻辑
            int available = switch (id) {
                case "PROD-001" -> 75;
                case "PROD-002" -> 120;
                case "PROD-003" -> 50;
                default -> 0;
            };

            int reserved = includeReserved ? 25 : 0;
            int total = available + reserved;

            // 4. 应用筛选条件
            if (total < minStock) {
                return new StockResult(id, name, region, 0, 0, 0);
            }

            return new StockResult(id, name, region, available, reserved, total);
        };
    }
}
