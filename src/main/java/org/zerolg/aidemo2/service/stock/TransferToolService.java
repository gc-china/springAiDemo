package org.zerolg.aidemo2.service.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.common.ToolExecutionResult;
import org.zerolg.aidemo2.service.InventoryService;
import org.zerolg.aidemo2.tools.InventoryTools;

@Service
public class TransferToolService {

    private static final Logger logger = LoggerFactory.getLogger(TransferToolService.class);
    private final InventoryService inventoryService;

    public TransferToolService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * 修改返回类型为 ToolExecutionResult
     */
    public ToolExecutionResult executeTransfer(InventoryTools.TransferRequest request) {
        boolean isConfirmed = request.confirmed() != null && request.confirmed();

        if (!isConfirmed) {
            // 🛑 阶段一：返回确认单 -> status: pending_confirmation
            logger.info("收到调拨请求，等待确认: {}", request);
            String confirmMsg = String.format("""
                            ⚠️ **操作确认**
                            您申请将 %d 个 [%s] 从 %s 调拨到 %s。
                            请回复“确认”以执行此操作，或回复“取消”以撤销。
                            """,
                    request.quantity(), request.product(), request.fromWarehouse(), request.toWarehouse());

            // payload 可以放结构化数据供前端展示，explain 给 LLM 阅读
            return ToolExecutionResult.pending(request, confirmMsg);
        } else {
            // ✅ 阶段二：执行操作
            try {
                inventoryService.transferStock(request.product(), request.fromWarehouse(), request.toWarehouse(), request.quantity());
                return ToolExecutionResult.success(
                        "Transfer completed",
                        "✅ 调拨执行成功！库存已更新。"
                );
            } catch (Exception e) {
                return ToolExecutionResult.error("❌ 执行失败: " + e.getMessage());
            }
        }
    }
}