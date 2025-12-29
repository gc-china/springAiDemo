package org.zerolg.aidemo2.tools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工具消歧控制器
 * 为LLM提供工具选择和使用指导
 */
@RestController
@RequestMapping("/api/tools")
@CrossOrigin(originPatterns = "*")
public class ToolDisambiguationController {

    @Autowired
    private ToolDisambiguationService toolDisambiguationService;

    /**
     * 获取所有可用工具信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, ToolDisambiguationService.ToolInfo>> getAllToolsInfo() {
        Map<String, ToolDisambiguationService.ToolInfo> toolsInfo = toolDisambiguationService.getAllToolsInfo();
        return ResponseEntity.ok(toolsInfo);
    }

    /**
     * 根据功能需求推荐工具
     */
    @GetMapping("/recommend")
    public ResponseEntity<List<ToolDisambiguationService.ToolRecommendation>> recommendTools(
            @RequestParam String functionality,
            @RequestParam(required = false) String context) {

        List<ToolDisambiguationService.ToolRecommendation> recommendations =
                toolDisambiguationService.recommendTools(functionality, context);

        return ResponseEntity.ok(recommendations);
    }

    /**
     * 获取工具使用指南
     */
    @GetMapping("/guide")
    public ResponseEntity<String> getToolUsageGuide() {
        String guide = toolDisambiguationService.getToolUsageGuide();
        return ResponseEntity.ok(guide);
    }

    /**
     * 获取工具选择建议（为LLM优化的格式）
     */
    @GetMapping("/selection-guide")
    public ResponseEntity<Map<String, Object>> getToolSelectionGuide() {
        Map<String, ToolDisambiguationService.ToolInfo> allTools = toolDisambiguationService.getAllToolsInfo();

        // 检查当前启用的工具
        boolean hasIntegrated = allTools.keySet().stream().anyMatch(name -> name.contains("integrated"));
        boolean hasAudited = allTools.keySet().stream().anyMatch(name -> name.contains("audited"));
        boolean hasEnhanced = allTools.keySet().stream().anyMatch(name -> name.contains("enhanced"));
        boolean hasBasic = allTools.keySet().stream().anyMatch(name -> name.contains("queryStock") && !name.contains("integrated") && !name.contains("audited") && !name.contains("smart"));

        Map<String, Object> selectionGuide = Map.of(
                "当前配置状态", "已优化 - 只启用最佳工具",
                "启用的工具类型", hasIntegrated ? "IntegratedInventoryTools (集成审计)" : "未检测到推荐工具",
                "工具状态检查", Map.of(
                        "IntegratedInventoryTools", hasIntegrated ? "✅ 已启用 (推荐)" : "❌ 未启用",
                        "AuditedInventoryTools", hasAudited ? "⚠️ 已启用 (建议禁用)" : "✅ 已禁用",
                        "EnhancedInventoryTools", hasEnhanced ? "⚠️ 已启用 (建议禁用)" : "✅ 已禁用",
                        "InventoryTools", hasBasic ? "⚠️ 已启用 (建议禁用)" : "✅ 已禁用"
                ),
                "推荐使用的工具", Map.of(
                        "库存查询", "integratedQueryStock - 集成参数清洗和审计的智能查询",
                        "库存调拨", "integratedTransferStock - 集成参数清洗和审计的智能调拨",
                        "参数追踪", "parameterTraceDemo - 演示参数清洗链路追踪"
                ),
                "工具优势", List.of(
                        "✅ 自动参数清洗和修正",
                        "✅ 完整的审计链路追踪",
                        "✅ 智能错误处理和歧义解决",
                        "✅ 性能监控和统计分析",
                        "✅ 避免工具选择困惑"
                ),
                "配置说明", Map.of(
                        "application.yml", "已配置只启用 tools.inventory.integrated.enabled=true",
                        "其他工具", "已通过 @ConditionalOnProperty 禁用",
                        "LLM建议", "直接使用 integrated 开头的工具即可"
                ),
                "详细工具信息", allTools
        );

        return ResponseEntity.ok(selectionGuide);
    }
}