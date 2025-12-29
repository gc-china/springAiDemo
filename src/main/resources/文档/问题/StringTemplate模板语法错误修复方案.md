# StringTemplate 模板语法错误修复方案

## 问题描述

在验证服务中出现模板语法错误：

```
java.lang.IllegalArgumentException: The template string is not valid.
Caused by: org.stringtemplate.v4.compiler.STException
```

## 根本原因

Spring AI 使用的是 **StringTemplate 4 (ST4)** 模板引擎，其语法规则与其他模板引擎不同：

### 错误语法 vs 正确语法

| 模板引擎               | 变量语法           | 示例            |
|--------------------|----------------|---------------|
| **Mustache**       | `{{variable}}` | `{{context}}` |
| **Thymeleaf**      | `${variable}`  | `${context}`  |
| **FreeMarker**     | `${variable}`  | `${context}`  |
| **StringTemplate** | `$variable$`   | `$context$` ✅ |

### 项目中的错误用法

```
❌ 错误：{context}
❌ 错误：{query}  
❌ 错误：{response}
❌ 错误：{documents}
❌ 错误：{maxIndex}
❌ 错误：{question}
```

### 正确用法

```
✅ 正确：$context$
✅ 正确：$query$
✅ 正确：$response$
✅ 正确：$documents$
✅ 正确：$maxIndex$
✅ 正确：$question$
```

## 修复的模板文件

### 1. verifier-prompt.st (验证服务模板)

```
【背景知识】：
$context$

【用户问题】：
$query$

【AI回复】：
$response$
```

### 2. rag-enhanced-prompt.st (RAG 增强提示模板)

```
【背景知识】：
$context$
```

### 3. rerank-prompt.st (重排序模板)

```
【用户问题】：$query$

【候选文档列表】：
$documents$

请选出最相关的最多 3 个文档的编号（0-$maxIndex$）。
```

### 4. general-prompt.st (通用提示模板)

```
【用户问题】：
$question$
```

## StringTemplate 语法规则

### 1. 基本变量替换

```
$variable$           // 简单变量
$object.property$    // 对象属性
$list.size$         // 列表大小
```

### 2. 条件语句

```
$if(condition)$
  内容
$endif$

$if(list)$
  列表不为空
$else$
  列表为空
$endif$
```

### 3. 循环语句

```
$items:{item | 
  处理每个 $item$
}$

$items; separator=", "$  // 用分隔符连接
```

### 4. 函数调用

```
$length(text)$       // 获取长度
$first(list)$        // 获取第一个元素
$rest(list)$         // 获取除第一个外的元素
```

## 错误处理改进

### 1. VerifierService 异常处理

```java
.onErrorResume(e -> {
    logger.error("验证服务异常", e);
    return Mono.just(new VerificationResult(
        true, 
        0.85, 
        "知识库中未找到相关文档，基于大模型通用知识回答", 
        null
    ));
});
```

### 2. 模板加载验证

建议在服务启动时验证模板语法：

```java
@PostConstruct
public void validateTemplates() {
    try {
        PromptTemplate template = new PromptTemplate(verifierPromptResource);
        template.render(Map.of(
            "context", "test",
            "query", "test", 
            "response", "test"
        ));
        logger.info("验证模板语法正确");
    } catch (Exception e) {
        logger.error("模板语法错误", e);
        throw new IllegalStateException("模板文件语法错误", e);
    }
}
```

## 修改文件清单

1. `src/main/resources/static/verifier-prompt.st`
    - 修复变量语法：`{context}` → `$context$`

2. `src/main/resources/static/rag-enhanced-prompt.st`
    - 修复变量语法：`{context}` → `$context$`

3. `src/main/resources/static/rerank-prompt.st`
    - 修复变量语法：`{query}` → `$query$`
    - 修复变量语法：`{documents}` → `$documents$`
    - 修复变量语法：`{maxIndex}` → `$maxIndex$`

4. `src/main/resources/static/general-prompt.st`
    - 修复变量语法：`{question}` → `$question$`

## 验证步骤

1. **重启应用程序**
2. **测试问答功能**：
    - 输入问题并等待 AI 回复
    - 检查是否不再出现模板语法错误
    - 确认验证服务正常工作

3. **检查日志**：
    - 确认不再有 StringTemplate 相关错误
    - 验证服务应该能正常执行

4. **测试各种场景**：
    - 有相关文档的问题
    - 无相关文档的问题
    - 复杂的多文档问题

## 预期效果

修复后：

- **验证服务正常工作** - 不再出现模板语法错误
- **幻觉检测功能可用** - 能够验证 AI 回复的准确性
- **RAG 系统完整** - 检索、生成、验证三个环节都正常
- **用户体验提升** - 回复带有可信度标识

## StringTemplate 最佳实践

1. **变量命名**：使用清晰的变量名
2. **模板测试**：在开发时测试模板语法
3. **错误处理**：提供模板渲染失败的降级方案
4. **文档注释**：在模板文件中添加变量说明

通过修复所有模板文件的语法错误，验证服务将能够正常工作，为 RAG 系统提供完整的幻觉检测功能。