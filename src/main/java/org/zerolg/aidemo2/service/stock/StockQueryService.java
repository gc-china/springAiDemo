package org.zerolg.aidemo2.service.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.common.ToolExecutionResult;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.tools.InventoryTools.StockQueryRequest;

@Service
public class StockQueryService {

    private static final Logger logger = LoggerFactory.getLogger(StockQueryService.class);
    private final InventoryService inventoryService;

    public StockQueryService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * ✅ 这个方法能被 AOP 拦截！
     * 职责单一：只负责查 ID，不负责猜测用户想搜什么（猜测是 Aspect 的事）
     */
    public ToolExecutionResult queryStock(StockQueryRequest request) {
        String productId = request.product();

        // 简单校验：如果不是 P- 开头，说明 Aspect 没生效或矫正失败
        if (!productId.startsWith("P-")) {
            // 这里可以做一个兜底的模糊查询，或者直接报错要求必须是 ID
            // 为了演示 AOP 的力量，我们这里假设必须要 ID
            return ToolExecutionResult.error("内部错误：参数未标准化 [" + productId + "]");
        }

        try {
            int stock = inventoryService.getStock(productId);
            return ToolExecutionResult.success(
                    stock, // payload
                    "产品ID [" + productId + "] 的当前库存为: " + stock // explain
            );
        } catch (Exception e) {
            return ToolExecutionResult.notFound("未找到产品ID: " + productId);
        }
    }
}