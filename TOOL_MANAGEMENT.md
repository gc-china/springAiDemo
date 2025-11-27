# Spring AI 工具管理最佳实践

## 问题
当有大量工具(如1000个)时,如何优雅地管理和注册这些工具?

## 解决方案

### 方案1: 自动扫描所有工具 ✅ 已实现

使用 `ToolRegistry` 自动扫描所有 `Function` Bean:

```java
@Autowired
private List<String> availableToolNames; // 自动注入所有工具名称

public AiService(ChatClient chatClient, List<String> availableToolNames) {
    this.availableTools = availableToolNames.toArray(new String[0]);
}
```

**优点:**
- 完全自动化,添加新工具无需修改代码
- 启动时就知道有哪些工具可用
- 适合工具数量多且相对稳定的场景

**缺点:**
- 所有工具都会传给 LLM,可能影响性能
- 无法根据场景动态选择

---

### 方案2: 按分类管理工具 ✅ 已实现

使用 `ToolCategories` 按业务分类:

```java
@Autowired
private ToolCategories toolCategories;

// 只使用产品相关工具
String[] productTools = toolCategories.getToolsArrayByCategories("product");

// 使用多个分类
String[] tools = toolCategories.getToolsArrayByCategories("product", "user");
```

**优点:**
- 可以根据场景选择相关工具
- 减少传给 LLM 的工具数量,提升性能
- 分类清晰,易于管理

**使用场景:**
- 不同业务模块使用不同工具集
- 需要根据用户权限限制工具访问
- 优化 LLM 性能

---

### 方案3: 智能工具选择 ✅ 示例已提供

根据查询内容动态选择工具(见 `SmartAiService.java`):

```java
private String[] selectToolsForQuery(String query) {
    if (query.contains("产品")) {
        return toolCategories.getToolsArrayByCategories("product");
    } else if (query.contains("用户")) {
        return toolCategories.getToolsArrayByCategories("user");
    }
    return toolCategories.getAllToolsArray();
}
```

**优点:**
- 最优性能,只传递相关工具
- 用户体验好,响应快
- 可以结合 NLP 做更智能的判断

**缺点:**
- 需要维护关键词规则
- 可能漏掉某些工具

---

### 方案4: 在 AiConfig 中全局配置

如果你的 Spring AI 版本支持,可以在 `ChatClient.Builder` 中配置:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder, List<String> availableToolNames) {
    return builder
            .defaultAdvisors(...)
            .defaultFunctions(availableToolNames.toArray(new String[0]))
            .build();
}
```

**注意:** 这个方法在某些版本可能不可用,需要测试。

---

## 推荐实践

### 小型项目 (< 10个工具)
直接手动指定:
```java
.toolNames("tool1", "tool2", "tool3")
```

### 中型项目 (10-100个工具)
使用**方案1**自动扫描 + **方案2**分类管理:
```java
// 自动加载所有工具
public AiService(List<String> availableToolNames) {
    this.availableTools = availableToolNames.toArray(new String[0]);
}

// 或者按分类使用
toolCategories.getToolsArrayByCategories("product", "user")
```

### 大型项目 (100+个工具)
使用**方案3**智能选择:
```java
// 根据查询内容动态选择工具
String[] tools = selectToolsForQuery(userQuery);
chatClient.prompt().toolNames(tools)...
```

---

## 性能优化建议

1. **限制工具数量**: 每次调用传递的工具不要超过 20-30 个
2. **使用分类**: 按业务领域分类,只传递相关工具
3. **缓存工具列表**: 工具列表在启动时加载,避免重复扫描
4. **监控工具调用**: 记录哪些工具被频繁使用,优化分类策略

---

## 扩展功能

### 1. 基于权限的工具过滤
```java
public String[] getToolsForUser(String userId) {
    UserRole role = getUserRole(userId);
    return toolCategories.getToolsByRole(role);
}
```

### 2. 工具使用统计
```java
@Aspect
public class ToolUsageTracker {
    public void trackToolUsage(String toolName) {
        // 记录工具使用次数
    }
}
```

### 3. 动态工具注册
```java
public void registerNewTool(String name, Function<?, ?> function) {
    // 运行时动态注册新工具
}
```
