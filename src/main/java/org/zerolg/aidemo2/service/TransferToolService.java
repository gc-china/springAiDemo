package org.zerolg.aidemo2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.tools.InventoryTools;
// 引入你的 TransferRequest 包
// import ...

/**
 * 独立的工具服务，用于承载被 AOP 拦截的业务逻辑
 */
@Service
public class TransferToolService {

    private static final Logger logger = LoggerFactory.getLogger(TransferToolService.class);
    private final InventoryService inventoryService;

    public TransferToolService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * ✅ 这个方法是 public 的，且在 Spring Bean 中
     * ✅ AOP 切面 (execution(* org.zerolg.aidemo2.tools.*.*(..))) 可以拦截到它！
     */
    public String executeTransfer(InventoryTools.TransferRequest request) {
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
                // 执行实际业务
                inventoryService.transferStock(request.product(), request.fromWarehouse(), request.toWarehouse(), request.quantity());
                return "✅ 调拨执行成功！";
            } catch (Exception e) {
                return "❌ 执行失败: " + e.getMessage();
            }
        }
    }
}