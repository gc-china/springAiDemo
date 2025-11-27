package org.zerolg.aidemo2.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

/**
 * 高级订单查询工具示例
 * 展示了如何使用 Record DTO 和详细注解来规范 LLM 的调用行为
 */
@Configuration
public class OrderTools {

    private static final Logger logger = LoggerFactory.getLogger(OrderTools.class);

    // 1. 定义强类型的请求参数 DTO (Data Transfer Object)
    public record OrderQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("订单号，通常以 'ORD' 开头，例如 ORD-2023-001")
            String orderId,

            @JsonProperty(required = false)
            @JsonPropertyDescription("查询详细程度，可选值：'BASIC' (仅状态), 'FULL' (包含物流详情)。默认为 BASIC")
            String detailLevel
    ) {}

    // 2. 定义强类型的返回结果 DTO
    public record OrderStatusResult(
            String orderId,
            String status,
            String description,
            String estimatedDelivery
    ) {}

    @Bean
    @Description("查询订单状态和详情。需要提供订单号，可选提供详细程度。")
    public Function<OrderQueryRequest, OrderStatusResult> getOrderStatus() {
        return request -> {
            // 此时 request 对象已经是类型安全的 Java 对象
            String orderId = request.orderId();
            String level = request.detailLevel() != null ? request.detailLevel() : "BASIC";

            logger.info(">>> 🔧 工具调用: 查询订单 [{}], 级别 [{}]", orderId, level);

            // 模拟业务逻辑
            if (orderId.startsWith("ORD")) {
                if ("FULL".equalsIgnoreCase(level)) {
                    return new OrderStatusResult(orderId, "SHIPPED", "您的订单已发货，当前在上海分拨中心", "2023-12-01");
                } else {
                    return new OrderStatusResult(orderId, "SHIPPED", "订单已发货", null);
                }
            } else {
                // 返回表示错误状态的结果，而不是抛出异常，这样 LLM 可以优雅地告诉用户
                return new OrderStatusResult(orderId, "NOT_FOUND", "未找到该订单，请检查订单号格式", null);
            }
        };
    }
}
