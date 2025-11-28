package org.zerolg.aidemo2.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册中心 - 自动发现和管理所有 AI 工具
 * 
 * 核心功能:
 * 1. 自动扫描所有 Function Bean (工具)
 * 2. 提供工具分类管理
 * 3. 支持按需获取工具列表
 */
@Configuration
public class ToolRegistry {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 🔍 核心方法: 自动扫描所有 Function Bean
     * 
     * 执行时机: Spring 启动时
     * 执行逻辑:
     *   1. 从 ApplicationContext 获取所有 Function 类型的 Bean
     *   2. 提取 Bean 名称 (即工具名称)
     *   3. 返回工具名称列表
     * 
     * 返回值会被 Spring 管理为一个 Bean,可以被其他类注入
     */
    @Bean
    public List<String> availableToolNames() {
        // 获取所有 Function Bean
        Map<String, Function> functionBeans = applicationContext.getBeansOfType(Function.class);
        
        // 直接返回所有 Function Bean 的名称
        // 注意：Spring AI 会自动处理 @Description，如果这里过滤错了，工具就丢了
        List<String> toolNames = new ArrayList<>(functionBeans.keySet());
        
        System.out.println(">>> 🔧 自动发现 " + toolNames.size() + " 个工具: " + toolNames);
        return toolNames;
    }

    /**
     * 检查 Bean 是否有 @Description 注解
     */
    private boolean hasDescriptionAnnotation(String beanName) {
        try {
            // 获取 Bean 的定义类
            Class<?> beanClass = applicationContext.getType(beanName);
            if (beanClass == null) return false;
            
            // 检查类上的方法是否有 @Description 注解
            for (Method method : beanClass.getDeclaredMethods()) {
                if (method.getName().equals(beanName) && 
                    method.isAnnotationPresent(Description.class)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;  // 出错则不包含
        }
    }

    /**
     * 🏷️ 工具分类管理器
     * 
     * 功能: 将工具按业务领域分类
     * 使用场景: 当工具很多时,可以按需选择相关工具
     */
    @Bean
    public ToolCategories toolCategories() {
        List<String> allTools = availableToolNames();
        
        ToolCategories categories = new ToolCategories();
        
        // 根据命名规则自动分类
        for (String toolName : allTools) {
            String lowerName = toolName.toLowerCase();
            
            if (lowerName.contains("product")) {
                categories.addTool("product", toolName);
            } else if (lowerName.contains("user")) {
                categories.addTool("user", toolName);
            } else if (lowerName.contains("order")) {
                categories.addTool("order", toolName);
            } else {
                categories.addTool("general", toolName);
            }
        }
        
        System.out.println(">>> 📂 工具分类完成: " + categories.getCategorySummary());
        return categories;
    }

    /**
     * 工具分类管理器
     */
    public static class ToolCategories {
        private final Map<String, List<String>> categories = new HashMap<>();

        public void addTool(String category, String toolName) {
            categories.computeIfAbsent(category, k -> new ArrayList<>()).add(toolName);
        }

        public List<String> getToolsByCategory(String category) {
            return categories.getOrDefault(category, Collections.emptyList());
        }

        public List<String> getAllTools() {
            return categories.values().stream()
                    .flatMap(List::stream)
                    .toList();
        }

        public String[] getAllToolsArray() {
            return getAllTools().toArray(new String[0]);
        }

        public String[] getToolsArrayByCategories(String... categoryNames) {
            return Arrays.stream(categoryNames)
                    .flatMap(cat -> getToolsByCategory(cat).stream())
                    .distinct()
                    .toArray(String[]::new);
        }
        
        public String getCategorySummary() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
                sb.append(entry.getKey()).append("(").append(entry.getValue().size()).append(") ");
            }
            return sb.toString();
        }
    }
}