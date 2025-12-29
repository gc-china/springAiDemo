package org.zerolg.aidemo2.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.function.Function;

/**
 * 工具配置信息组件
 * 用于启动时显示当前启用的工具
 */
@Component
public class ToolConfigurationInfo {

    private static final Logger logger = LoggerFactory.getLogger(ToolConfigurationInfo.class);

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void logToolConfiguration() {
        logger.info("=== 工具配置信息 ===");

        // 检查各个工具类是否被加载
        checkToolClass("InventoryTools", "基础库存工具");
        checkToolClass("EnhancedInventoryTools", "增强库存工具");
        checkToolClass("AuditedInventoryTools", "审计库存工具");
        checkToolClass("IntegratedInventoryTools", "集成审计库存工具");

        // 统计Function类型的Bean数量
        var functionBeans = applicationContext.getBeansOfType(Function.class);
        logger.info("当前注册的Function工具数量: {}", functionBeans.size());

        // 列出所有Function Bean名称
        functionBeans.keySet().forEach(beanName -> {
            logger.info("- 已注册工具: {}", beanName);
        });

        logger.info("=== 推荐使用集成审计工具 ===");
        logger.info("- integratedQueryStock: 集成审计的库存查询");
        logger.info("- integratedTransferStock: 集成审计的库存调拨");
        logger.info("- parameterTraceDemo: 参数清洗链路追踪演示");
        logger.info("========================");
    }

    private void checkToolClass(String className, String description) {
        try {
            String fullClassName = "org.zerolg.aidemo2.tools." + className;
            if (applicationContext.getBeanNamesForType(Class.forName(fullClassName)).length > 0) {
                logger.info("✅ {} ({}) - 已启用", className, description);
            } else {
                logger.info("❌ {} ({}) - 已禁用", className, description);
            }
        } catch (Exception e) {
            logger.info("❌ {} ({}) - 已禁用", className, description);
        }
    }
}