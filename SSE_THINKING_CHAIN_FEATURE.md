# SSE思维链展示功能

## 功能概述

实现了自定义SSE协议，支持思维链、工具调用、歧义处理等多种消息类型的流式展示。

## SSE消息类型

### 1. thinking - 思维链消息

展示AI的思考过程，包括：

- `retrieval`: 文档检索阶段
- `reasoning`: 推理分析阶段
- `tool_call`: 工具调用阶段
- `verification`: 结果验证阶段

```json
{
  "type": "thinking",
  "stage": "retrieval",
  "delta": "正在检索相关文档...",
  "seq": 0
}
```

### 2. content - 内容消息

AI回答的增量内容

```json
{
  "type": "content",
  "delta": "根据检索结果...",
  "seq": 1
}
```

### 3. tool - 工具调用消息

工具调用和结果

```json
{
  "type": "tool",
  "stage": "tool_call",
  "seq": 2,
  "meta": {
    "toolName": "integratedQueryStock",
    "params": {"product": "iPhone15"}
  }
}
```

### 4. ambiguous - 歧义消息

需要用户选择的候选项

```json
{
  "type": "ambiguous",
  "stage": "tool_call",
  "delta": "产品名称存在歧义，请选择",
  "seq": 3,
  "meta": {
    "toolName": "integratedQueryStock",
    "candidates": ["iPhone 15", "iPhone 15 Pro"]
  }
}
```

### 5. citations - 引用消息

文档引用信息

```json
{
  "type": "citations",
  "seq": 4,
  "meta": {
    "citations": [...]
  }
}
```

### 6. verification - 验证消息

回答准确性验证结果

```json
{
  "type": "verification",
  "seq": 5,
  "meta": {
    "result": {
      "passed": true,
      "confidence": 0.95
    }
  }
}
```

## 前端实现

### 思维链卡片

- 可折叠展示
- 不同阶段使用不同图标
- 实时流式更新

### 工具调用展示

- 显示工具名称和参数
- 显示调用状态（调用中/完成）
- 显示执行结果

### 歧义处理

- 显示候选项按钮
- 用户点击选择
- 重新发起请求

## 后端实现

### AiService.java

- 使用`AtomicInteger`维护序列号
- 在不同阶段发送对应的思维链消息
- 统一的SSE消息构建方法

### SseMessage.java

- 统一的消息格式定义
- 静态工厂方法创建不同类型消息
- 支持元数据扩展

## 使用示例

### 后端发送思维链

```java
Flux<ServerSentEvent<String>> thinkingStart = Flux.just(
    buildSseEvent(SseMessage.thinking("retrieval", "正在检索相关文档...", seqCounter.getAndIncrement()))
);
```

### 前端接收处理

```typescript
eventSource.addEventListener('thinking', (e) => {
  const message = JSON.parse(e.data);
  aiMsg.thinking.push({
    stage: message.stage,
    content: message.delta,
    seq: message.seq
  });
});
```

## 特性

✅ 流式渲染Markdown内容
✅ 思维链卡片化展示
✅ 工具调用可视化
✅ 歧义候选按钮交互
✅ 引用来源展示
✅ 验证结果标识
✅ 支持取消请求
✅ 响应式设计

## 兼容性

- 向后兼容旧的`message`事件
- 自动降级处理解析失败
- 支持新旧格式混合
