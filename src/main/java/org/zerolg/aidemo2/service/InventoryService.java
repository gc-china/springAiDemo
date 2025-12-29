package org.zerolg.aidemo2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟库存业务服务
 */
@Service
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    // 模拟库存数据: ID -> 数量
    private final Map<String, Integer> stock = new HashMap<>();

    // 模拟预留库存数据
    private final Map<String, Integer> reservedStock = new HashMap<>();

    // 模拟产品名称映射
    private final Map<String, String> productNames = new HashMap<>();

    public InventoryService() {
        // 初始化库存数据
        stock.put("P-001", 100); // iPhone 15
        stock.put("P-002", 50);  // iPhone 15 Pro
        stock.put("P-003", 30);  // MacBook
        stock.put("P-004", 200); // Sony
        stock.put("P-005", 80);  // Dyson

        // 初始化预留库存
        reservedStock.put("P-001", 10);
        reservedStock.put("P-002", 5);
        reservedStock.put("P-003", 3);
        reservedStock.put("P-004", 20);
        reservedStock.put("P-005", 8);

        // 初始化产品名称
        productNames.put("P-001", "iPhone 15");
        productNames.put("P-002", "iPhone 15 Pro");
        productNames.put("P-003", "MacBook Pro");
        productNames.put("P-004", "Sony Camera");
        productNames.put("P-005", "Dyson Vacuum");
    }

    /**
     * 查询库存 (读操作)
     */
    public int getStock(String productId) {
        return stock.getOrDefault(productId, 0);
    }

    /**
     * 获取预留库存
     */
    public int getReservedStock(String productId) {
        return reservedStock.getOrDefault(productId, 0);
    }

    /**
     * 获取产品名称
     */
    public String getProductName(String productId) {
        return productNames.getOrDefault(productId, "Unknown Product");
    }

    /**
     * 获取所有产品ID列表
     */
    public List<String> getAllProductIds() {
        return List.copyOf(stock.keySet());
    }

    /**
     * 调拨库存 (写操作) - 返回boolean
     */
    public boolean transferStock(String productId, String from, String to, int quantity) {
        logger.info(">>> 🚚 执行调拨: 将 {} 个 [{}] 从 {} 发往 {}", quantity, productId, from, to);

        try {
            // 简单的扣减逻辑
            int current = getStock(productId);
            if (current >= quantity) {
                stock.put(productId, current - quantity);
                logger.info("    调拨成功，剩余库存: {}", current - quantity);
                return true;
            } else {
                logger.warn("    库存不足！当前: {}, 需要: {}", current, quantity);
                return false;
            }
        } catch (Exception e) {
            logger.error("调拨异常", e);
            return false;
        }
    }
}
