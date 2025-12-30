// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.service;

// 导入日志相关类，用于记录业务操作过程
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// 导入Spring框架注解
import org.springframework.stereotype.Service;

// 导入Java集合类
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟库存业务服务
 *
 * 这是一个模拟的库存管理服务，用于演示AI工具调用功能
 * 主要功能包括：
 * 1. 库存查询 - 查询产品的可用库存数量
 * 2. 预留库存管理 - 管理已预留但未出库的库存
 * 3. 库存调拨 - 在不同仓库或位置之间转移库存
 * 4. 产品信息管理 - 维护产品ID与产品名称的映射关系
 *
 * 注意：这是一个内存模拟实现，主要用于演示和测试
 * 在生产环境中，这些数据应该存储在数据库中
 *
 * 设计特点：
 * - 线程安全：使用HashMap存储数据（单线程环境下安全）
 * - 简单易懂：业务逻辑清晰，便于理解AI工具调用机制
 * - 可扩展：可以轻松添加更多库存相关功能
 */
@Service // Spring注解：标记这是一个服务层组件
public class InventoryService {

    // 创建日志记录器，用于记录库存操作过程
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    // 模拟库存数据存储：产品ID -> 库存数量的映射
    // 在实际项目中，这些数据应该存储在数据库中
    private final Map<String, Integer> stock = new HashMap<>();

    // 模拟预留库存数据存储：产品ID -> 预留数量的映射
    // 预留库存是指已被订单占用但尚未实际出库的库存
    private final Map<String, Integer> reservedStock = new HashMap<>();

    // 模拟产品名称映射：产品ID -> 产品名称的映射
    // 用于提供更友好的产品信息显示
    private final Map<String, String> productNames = new HashMap<>();

    /**
     * 构造函数 - 初始化模拟数据
     * <p>
     * 在服务启动时预置一些测试数据，包括：
     * - 各种产品的库存数量
     * - 对应的预留库存数量
     * - 产品ID与产品名称的映射关系
     */
    public InventoryService() {
        // 初始化库存数据 - 设置各产品的可用库存数量
        stock.put("P-001", 100); // iPhone 15 - 100台
        stock.put("P-002", 50);  // iPhone 15 Pro - 50台
        stock.put("P-003", 30);  // MacBook - 30台
        stock.put("P-004", 200); // Sony相机 - 200台
        stock.put("P-005", 80);  // Dyson吸尘器 - 80台

        // 初始化预留库存 - 设置各产品的预留数量
        reservedStock.put("P-001", 10); // iPhone 15预留10台
        reservedStock.put("P-002", 5);  // iPhone 15 Pro预留5台
        reservedStock.put("P-003", 3);  // MacBook预留3台
        reservedStock.put("P-004", 20); // Sony相机预留20台
        reservedStock.put("P-005", 8);  // Dyson吸尘器预留8台

        // 初始化产品名称映射 - 建立产品ID与友好名称的对应关系
        productNames.put("P-001", "iPhone 15");
        productNames.put("P-002", "iPhone 15 Pro");
        productNames.put("P-003", "MacBook Pro");
        productNames.put("P-004", "Sony Camera");
        productNames.put("P-005", "Dyson Vacuum");
    }

    /**
     * 查询库存 (读操作)
     *
     * 根据产品ID查询当前可用库存数量
     * 这是一个只读操作，不会修改库存数据
     *
     * @param productId 产品ID，如"P-001"
     * @return 库存数量，如果产品不存在则返回0
     */
    public int getStock(String productId) {
        // 使用getOrDefault方法，如果产品ID不存在则返回默认值0
        return stock.getOrDefault(productId, 0);
    }

    /**
     * 获取预留库存
     *
     * 查询指定产品的预留库存数量
     * 预留库存是指已被订单占用但尚未实际出库的数量
     *
     * @param productId 产品ID
     * @return 预留库存数量，如果产品不存在则返回0
     */
    public int getReservedStock(String productId) {
        return reservedStock.getOrDefault(productId, 0);
    }

    /**
     * 获取产品名称
     *
     * 根据产品ID获取对应的产品友好名称
     * 用于在用户界面显示更易读的产品信息
     *
     * @param productId 产品ID
     * @return 产品名称，如果产品不存在则返回"Unknown Product"
     */
    public String getProductName(String productId) {
        return productNames.getOrDefault(productId, "Unknown Product");
    }

    /**
     * 获取所有产品ID列表
     *
     * 返回系统中所有可用产品的ID列表
     * 用于产品列表展示或批量操作
     *
     * @return 产品ID列表的不可变副本
     */
    public List<String> getAllProductIds() {
        // 使用List.copyOf创建不可变列表，防止外部修改
        return List.copyOf(stock.keySet());
    }

    /**
     * 调拨库存 (写操作) - 返回boolean
     *
     * 执行库存调拨操作，将指定数量的产品从一个位置转移到另一个位置
     * 这是一个写操作，会实际修改库存数据
     *
     * 业务逻辑：
     * 1. 检查源位置是否有足够的库存
     * 2. 如果库存充足，则扣减相应数量
     * 3. 记录调拨操作日志
     * 4. 返回操作结果
     *
     * 注意：这里简化了调拨逻辑，实际项目中可能需要：
     * - 更复杂的库存检查（考虑预留库存）
     * - 目标位置的库存增加
     * - 调拨记录的持久化存储
     * - 事务管理确保数据一致性
     *
     * @param productId 产品ID，要调拨的产品
     * @param from 源位置，库存的来源地
     * @param to 目标位置，库存的目的地
     * @param quantity 调拨数量，要转移的产品数量
     * @return true表示调拨成功，false表示调拨失败（通常是库存不足）
     */
    public boolean transferStock(String productId, String from, String to, int quantity) {
        // 记录调拨操作开始的日志，使用emoji增强可读性
        logger.info(">>> 🚚 执行调拨: 将 {} 个 [{}] 从 {} 发往 {}", quantity, productId, from, to);

        try {
            // 获取当前库存数量
            int current = getStock(productId);

            // 检查库存是否充足
            if (current >= quantity) {
                // 库存充足，执行扣减操作
                stock.put(productId, current - quantity);
                logger.info("    调拨成功，剩余库存: {}", current - quantity);
                return true; // 返回成功
            } else {
                // 库存不足，记录警告日志
                logger.warn("    库存不足！当前: {}, 需要: {}", current, quantity);
                return false; // 返回失败
            }
        } catch (Exception e) {
            // 捕获任何异常，记录错误日志
            logger.error("调拨异常", e);
            return false; // 异常情况下返回失败
        }
    }
}
