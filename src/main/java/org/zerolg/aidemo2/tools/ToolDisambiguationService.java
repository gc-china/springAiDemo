package org.zerolg.aidemo2.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具消歧服务
 * 帮助LLM识别和选择正确的工具
 */
@Service
public class ToolDisambiguationService {

    private static final Logger logger = LoggerFactory.getLogger(ToolDisambiguationService.class);

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    /**
     * 获取所有可用工具的详细信息
     */
    public Map<String, ToolInfo> getAllToolsInfo() {
        Map<String, ToolInfo> toolsInfo = new HashMap<>();

        // 获取所有Function类型的Bean
        Map<String, Function> functionBeans = applicationContext.getBeansOfType(Function.class);

        for (Map.Entry<String, Function> entry : functionBeans.entrySet()) {
            String beanName = entry.getKey();
            Function<?, ?> function = entry.getValue();

            try {
                // 简化实现，直接基于Bean名称推断工具信息
                String sourceClassName = inferSourceClassName(beanName);
                ToolInfo toolInfo = createToolInfo(beanName, function, sourceClassName);
                toolsInfo.put(beanName, toolInfo);

            } catch (Exception e) {
                logger.warn("Failed to analyze tool: {}", beanName, e);
            }
        }

        return toolsInfo;
    }

    /**
     * 根据功能需求推荐最合适的工具
     */
    public List<ToolRecommendation> recommendTools(String functionality, String context) {
        Map<String, ToolInfo> allTools = getAllToolsInfo();

        List<ToolRecommendation> recommendations = new ArrayList<>();

        for (Map.Entry<String, ToolInfo> entry : allTools.entrySet()) {
            String toolName = entry.getKey();
            ToolInfo toolInfo = entry.getValue();

            double score = calculateRelevanceScore(toolInfo, functionality, context);
            if (score > 0.3) { // 只推荐相关度较高的工具
                recommendations.add(new ToolRecommendation(
                        toolName,
                        toolInfo,
                        score,
                        generateRecommendationReason(toolInfo, functionality, context)
                ));
            }
        }

        // 按相关度排序
        recommendations.sort((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()));

        return recommendations.stream().limit(5).collect(Collectors.toList()); // 最多返回5个推荐
    }

    /**
     * 获取工具使用指南
     */
    public String getToolUsageGuide() {
        Map<String, ToolInfo> allTools = getAllToolsInfo();

        StringBuilder guide = new StringBuilder();
        guide.append("# 工具使用指南\n\n");
        guide.append("## 🎯 推荐工具（已优化配置）\n\n");
        guide.append("系统已配置为只启用最优的集成审计工具，避免选择困惑：\n\n");

        // 检查IntegratedInventoryTools是否启用
        boolean hasIntegratedTools = allTools.keySet().stream()
                .anyMatch(name -> name.contains("integrated"));

        if (hasIntegratedTools) {
            guide.append("### ✅ 当前启用的工具\n");
            guide.append("- **integratedQueryStock**: 集成参数清洗和审计的库存查询（推荐使用）\n");
            guide.append("- **integratedTransferStock**: 集成参数清洗和审计的库存调拨（推荐使用）\n");
            guide.append("- **parameterTraceDemo**: 参数清洗链路追踪演示\n\n");
            guide.append("### 🔧 功能特点\n");
            guide.append("- 自动参数清洗和修正\n");
            guide.append("- 完整的审计链路追踪\n");
            guide.append("- 智能错误处理和歧义解决\n");
            guide.append("- 性能监控和统计\n\n");
        }

        // 按类别分组显示所有工具
        Map<String, List<Map.Entry<String, ToolInfo>>> toolsByCategory = allTools.entrySet().stream()
                .collect(Collectors.groupingBy(entry -> entry.getValue().category()));

        guide.append("## 📊 所有工具详情\n\n");

        for (Map.Entry<String, List<Map.Entry<String, ToolInfo>>> categoryEntry : toolsByCategory.entrySet()) {
            String category = categoryEntry.getKey();
            List<Map.Entry<String, ToolInfo>> tools = categoryEntry.getValue();

            guide.append("### ").append(category).append("\n\n");

            for (Map.Entry<String, ToolInfo> toolEntry : tools) {
                String toolName = toolEntry.getKey();
                ToolInfo toolInfo = toolEntry.getValue();

                guide.append("#### ").append(toolName);
                if (toolName.contains("integrated")) {
                    guide.append(" ⭐ (推荐)");
                }
                guide.append("\n");
                guide.append("- **描述**: ").append(toolInfo.description()).append("\n");
                guide.append("- **功能**: ").append(String.join(", ", toolInfo.capabilities())).append("\n");
                guide.append("- **适用场景**: ").append(toolInfo.usageScenario()).append("\n");
                guide.append("- **优先级**: ").append(toolInfo.priority()).append("\n\n");
            }
        }

        guide.append("## 💡 使用建议\n\n");
        guide.append("1. **优先使用**: 以 `integrated` 开头的工具（已启用）\n");
        guide.append("2. **功能最全**: 集成了参数清洗、审计、性能监控\n");
        guide.append("3. **问题诊断**: 可通过审计面板查看详细执行过程\n");
        guide.append("4. **性能优化**: 系统会自动记录和分析性能数据\n\n");

        return guide.toString();
    }

    private String inferSourceClassName(String beanName) {
        // 基于Bean名称推断源类名
        if (beanName.contains("integrated")) {
            return "org.zerolg.aidemo2.tools.IntegratedInventoryTools";
        } else if (beanName.contains("audited")) {
            return "org.zerolg.aidemo2.tools.AuditedInventoryTools";
        } else if (beanName.contains("smart") || beanName.contains("batch") || beanName.contains("enhanced")) {
            return "org.zerolg.aidemo2.tools.EnhancedInventoryTools";
        } else if (beanName.contains("query") || beanName.contains("transfer")) {
            return "org.zerolg.aidemo2.tools.InventoryTools";
        }
        return "org.zerolg.aidemo2.tools.UnknownTools";
    }

    private ToolInfo createToolInfo(String beanName, Function<?, ?> function, String sourceClassName) {
        // 分析工具信息
        String category = determineCategory(beanName, sourceClassName);
        String description = extractDescription(beanName, sourceClassName);
        List<String> capabilities = extractCapabilities(beanName, description);
        String usageScenario = determineUsageScenario(beanName, description);
        int priority = determinePriority(beanName, sourceClassName);

        return new ToolInfo(
                beanName,
                description,
                category,
                capabilities,
                usageScenario,
                priority,
                sourceClassName
        );
    }

    private String determineCategory(String beanName, String sourceClassName) {
        if (sourceClassName.contains("Inventory")) {
            return "库存管理";
        } else if (sourceClassName.contains("Audit")) {
            return "审计监控";
        } else if (sourceClassName.contains("Enhanced")) {
            return "增强功能";
        } else if (sourceClassName.contains("Integrated")) {
            return "集成功能";
        }
        return "通用工具";
    }

    private String extractDescription(String beanName, String sourceClassName) {
        // 这里可以通过反射或配置文件获取描述
        // 简化实现，基于命名推断
        if (beanName.contains("query") || beanName.contains("Query")) {
            return "库存查询工具";
        } else if (beanName.contains("transfer") || beanName.contains("Transfer")) {
            return "库存调拨工具";
        } else if (beanName.contains("audit") || beanName.contains("Audit")) {
            return "审计功能工具";
        }
        return "通用功能工具";
    }

    private List<String> extractCapabilities(String beanName, String description) {
        List<String> capabilities = new ArrayList<>();

        if (beanName.contains("smart") || beanName.contains("Smart")) {
            capabilities.add("智能处理");
        }
        if (beanName.contains("enhanced") || beanName.contains("Enhanced")) {
            capabilities.add("增强功能");
        }
        if (beanName.contains("integrated") || beanName.contains("Integrated")) {
            capabilities.add("集成审计");
        }
        if (beanName.contains("audited") || beanName.contains("Audited")) {
            capabilities.add("完整审计");
        }
        if (beanName.contains("batch") || beanName.contains("Batch")) {
            capabilities.add("批量处理");
        }

        if (capabilities.isEmpty()) {
            capabilities.add("基础功能");
        }

        return capabilities;
    }

    private String determineUsageScenario(String beanName, String description) {
        if (beanName.contains("audited") || beanName.contains("Audited")) {
            return "需要完整审计追踪的场景";
        } else if (beanName.contains("integrated") || beanName.contains("Integrated")) {
            return "需要参数清洗和审计的场景";
        } else if (beanName.contains("enhanced") || beanName.contains("Enhanced")) {
            return "需要增强功能的场景";
        } else if (beanName.contains("smart") || beanName.contains("Smart")) {
            return "需要智能处理的场景";
        }
        return "一般业务场景";
    }

    private int determinePriority(String beanName, String sourceClassName) {
        // 优先级：集成 > 审计 > 增强 > 基础
        if (sourceClassName.contains("Integrated")) {
            return 1; // 最高优先级
        } else if (sourceClassName.contains("Audited")) {
            return 2;
        } else if (sourceClassName.contains("Enhanced")) {
            return 3;
        }
        return 4; // 基础工具最低优先级
    }

    private double calculateRelevanceScore(ToolInfo toolInfo, String functionality, String context) {
        double score = 0.0;

        // 基于描述的相关性
        if (toolInfo.description().toLowerCase().contains(functionality.toLowerCase())) {
            score += 0.5;
        }

        // 基于功能的相关性
        for (String capability : toolInfo.capabilities()) {
            if (capability.toLowerCase().contains(functionality.toLowerCase())) {
                score += 0.3;
            }
        }

        // 基于上下文的相关性
        if (context != null) {
            if (toolInfo.usageScenario().toLowerCase().contains(context.toLowerCase())) {
                score += 0.2;
            }
        }

        // 优先级加权
        score += (5 - toolInfo.priority()) * 0.1;

        return Math.min(score, 1.0);
    }

    private String generateRecommendationReason(ToolInfo toolInfo, String functionality, String context) {
        StringBuilder reason = new StringBuilder();
        reason.append("推荐理由: ");

        if (toolInfo.description().toLowerCase().contains(functionality.toLowerCase())) {
            reason.append("功能匹配度高; ");
        }

        reason.append("优先级: ").append(toolInfo.priority()).append("; ");
        reason.append("适用场景: ").append(toolInfo.usageScenario());

        return reason.toString();
    }

    // 数据类
    public record ToolInfo(
            String name,
            String description,
            String category,
            List<String> capabilities,
            String usageScenario,
            int priority,
            String sourceClass
    ) {
    }

    public record ToolRecommendation(
            String toolName,
            ToolInfo toolInfo,
            double relevanceScore,
            String reason
    ) {
    }
}