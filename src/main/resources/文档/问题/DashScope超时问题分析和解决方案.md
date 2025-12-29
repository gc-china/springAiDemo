# DashScope 超时问题分析和解决方案

## 🚨 问题现象

```
I/O error on POST request for "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation": timeout
Retry error. Retry count: 1
```

## 🔍 超时原因分析

### 1. 网络层面原因

- **网络延迟高**：到阿里云服务器的网络延迟
- **带宽限制**：上传大量文本内容时带宽不足
- **DNS解析慢**：域名解析延迟
- **防火墙/代理**：企业网络限制

### 2. API 层面原因

- **请求量大**：同时发送多个请求（RAG检索 + 验证 + 断言分析）
- **内容过长**：传递给 LLM 的 prompt 过长
- **模型负载高**：qwen-max 模型服务器负载高
- **API 限流**：触发了 API 调用频率限制

### 3. 应用层面原因

- **超时设置过短**：默认超时时间不够
- **并发请求**：多个功能同时调用 API
- **重试机制**：重试间隔设置不当

## 🔧 解决方案

### 1. 调整超时和重试配置

#### 当前配置

```yaml
spring:
  ai:
    retry:
      max-attempts: 3
      backoff:
        initial-interval: 1000ms
        max-interval: 10000ms
        multiplier: 2.0
```

#### 优化配置

```yaml
spring:
  ai:
    dashscope:
      api-key: sk-9542ddc0ec1e40c0a4f1b0d3d389e778
      chat:
        options:
          model: qwen-max
          # 增加超时时间
          timeout: 60s
      embedding:
        options:
          model: text-embedding-v1
          timeout: 30s
    retry:
      max-attempts: 5              # 增加重试次数
      backoff:
        initial-interval: 2000ms   # 增加初始间隔
        max-interval: 30000ms      # 增加最大间隔
        multiplier: 2.0
```

### 2. 请求优化策略

#### 减少并发请求

```java
// 在 AiService 中添加请求队列
@Service
public class AiService {
    private final Semaphore apiSemaphore = new Semaphore(2); // 限制并发数

    public Flux<ServerSentEvent<String>> processQuery(String chatId, String msg) {
        return Mono.fromCallable(() -> {
                    apiSemaphore.acquire(); // 获取许可
                    return "acquired";
                })
                .flatMapMany(acquired -> {
                    // 原有逻辑
                    return actualProcessQuery(chatId, msg);
                })
                .doFinally(signalType -> {
                    apiSemaphore.release(); // 释放许可
                });
    }
}
```

#### 内容长度优化

```java
// 限制传递给 LLM 的内容长度
private String truncateContent(String content, int maxLength) {
    if (content.length() <= maxLength) {
        return content;
    }
    return content.substring(0, maxLength) + "...(内容已截断)";
}
```

### 3. 网络层优化

#### HTTP 客户端配置

```yaml
spring:
  ai:
    dashscope:
      # HTTP 客户端配置
      http-client:
        connect-timeout: 10s
        read-timeout: 60s
        write-timeout: 30s
        # 连接池配置
        max-connections: 20
        max-connections-per-route: 5
```

#### 连接复用

```java

@Configuration
public class HttpClientConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build();
    }
}
```

### 4. 智能降级策略

#### 功能优先级

```
1. 主对话生成 (最高优先级)
2. 文档检索
3. 简单验证
4. 详细验证 (可选)
5. 断言分析 (可选)
```

#### 降级实现

```java
public Mono<DetailedVerificationResult> verifyDetailed(String query, List<Document> documents, String response) {
    return Mono.fromCallable(() -> performDetailedVerification(query, documents, response))
            .timeout(Duration.ofSeconds(15))
            .onErrorResume(TimeoutException.class, e -> {
                logger.warn("详细验证超时，降级到简单验证");
                return simpleVerify(query, documents, response)
                        .map(this::convertToDetailedResult);
            })
            .onErrorResume(Exception.class, e -> {
                logger.error("验证服务异常，使用默认结果", e);
                return Mono.just(createDefaultResult());
            });
}
```

### 5. 缓存策略

#### LLM 响应缓存

```java

@Service
public class LLMCacheService {

    private final Cache<String, String> responseCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    public Mono<String> getCachedResponse(String prompt) {
        String cached = responseCache.getIfPresent(prompt);
        if (cached != null) {
            return Mono.just(cached);
        }

        return callLLM(prompt)
                .doOnNext(response -> responseCache.put(prompt, response));
    }
}
```

## 📊 监控和诊断

### 1. 添加性能监控

```java

@Component
public class ApiMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer apiCallTimer;
    private final Counter timeoutCounter;

    public ApiMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.apiCallTimer = Timer.builder("dashscope.api.call")
                .description("DashScope API call duration")
                .register(meterRegistry);
        this.timeoutCounter = Counter.builder("dashscope.api.timeout")
                .description("DashScope API timeout count")
                .register(meterRegistry);
    }

    public void recordTimeout() {
        timeoutCounter.increment();
    }
}
```

### 2. 健康检查

```java

@Component
public class DashScopeHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            // 简单的健康检查请求
            String response = chatClient.prompt()
                    .user("ping")
                    .call()
                    .content();

            return Health.up()
                    .withDetail("status", "available")
                    .withDetail("response_time", "< 5s")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
```

## 🎯 实施建议

### 立即实施

1. **增加超时时间**：chat 60s, embedding 30s
2. **优化重试配置**：增加重试次数和间隔
3. **添加并发限制**：限制同时调用的 API 数量

### 短期实施

1. **内容长度限制**：避免发送过长的 prompt
2. **智能降级**：超时时自动降级到简单功能
3. **性能监控**：添加 API 调用监控

### 长期优化

1. **缓存策略**：缓存常见的 LLM 响应
2. **负载均衡**：考虑使用多个 API key 或服务商
3. **本地模型**：考虑部署本地模型作为备用

## 🔍 根本解决思路

### 1. 网络优化

- 使用 CDN 或就近接入点
- 优化网络配置和 DNS
- 考虑专线连接

### 2. 架构优化

- 异步处理非关键功能
- 实现请求队列和限流
- 增加缓存层

### 3. 业务优化

- 简化 prompt 内容
- 减少不必要的 API 调用
- 优化用户体验设计

通过这些优化措施，可以显著减少 DashScope API 的超时问题，提升系统的稳定性和用户体验。