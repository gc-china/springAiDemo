package org.zerolg.aidemo2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 模拟库存业务服务
 */
@Service
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    // 模拟库存数据: ID -> 数量
    private final Map<String, Integer> stock = new HashMap<>();

    public InventoryService() {
        stock.put("P-001", 100); // iPhone 15
        stock.put("P-002", 50);  // iPhone 15 Pro
        stock.put("P-003", 30);  // MacBook
        stock.put("P-004", 200); // Sony
        stock.put("P-005", 80);  // Dyson
    }

    /**
     * 查询库存 (读操作)
     */
    public int getStock(String productId) {
        return stock.getOrDefault(productId, 0);
    }

    /**
     * 调拨库存 (写操作)
     */
    public void transferStock(String productId, String from, String to, int quantity) {
        logger.info(">>> 🚚 执行调拨: 将 {} 个 [{}] 从 {} 发往 {}", quantity, productId, from, to);
        
        // 简单的扣减逻辑
        int current = getStock(productId);
        if (current >= quantity) {
            stock.put(productId, current - quantity);
            logger.info("    调拨成功，剩余库存: {}", current - quantity);
        } else {
            logger.warn("    库存不足！当前: {}, 需要: {}", current, quantity);
            throw new RuntimeException("库存不足");
        }
    }
}
