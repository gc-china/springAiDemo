# AIDemo2 - 企业级 AI 智能助手演示平台

## 1. 项目简介 (Project Introduction)
AIDemo2 是一个基于 **Spring Boot 3.3** 和 **Spring AI** 构建的企业级 AI 应用脚手架。它演示了如何构建一个具备 **RAG (检索增强生成)**、**Tool Calling (工具调用)** 和 **AOP 参数自矫正** 能力的智能 Agent 系统。

本项目旨在展示从 Demo 到企业级应用的演进过程，集成了统一的 API 规范、全局异常处理、请求日志追踪等企业级特性。

### 核心特性 (Key Features)
- **🧠 混合智能架构**: 结合 RAG（知识库检索）与 Function Calling（工具调用），实现既懂知识又能干活的 AI。
- **🛡️ 防御性 AI 设计**:
    - **AOP 参数矫正**: 利用切面技术和搜索引擎自动纠正用户的模糊输入（如将 "iPhone 15" 自动转换为 ID "P-001"）。
    - **Human-in-the-loop**: 敏感操作（如库存调拨）内置人机确认流程，防止 AI 幻觉带来的风险。
- **🏗️ 企业级工程规范**:
    - **统一响应体**: 标准化的 `ApiResponse<T>` 结构。
    - **全局异常处理**: 分级异常捕获与标准化错误码。
    - **可观测性**: 集成 MDC TraceId 和请求日志切面。
- **🔌 自动工具发现**: 基于 Spring Bean 的 `ToolRegistry` 实现工具的自动扫描与注册，零配置接入新工具。

## 2. 技术栈 (Tech Stack)
- **核心框架**: Spring Boot 3.3.5
- **AI 框架**: Spring AI 1.0.0 (支持 Ollama, Alibaba DashScope 等)
- **响应式编程**: Project Reactor (Flux/Mono)
- **构建工具**: Maven
- **开发语言**: Java 17+

## 3. 快速开始 (Getting Started)

### 3.1 环境要求
- JDK 17+
- Maven 3.8+
- Alibaba DashScope API Key (或其他兼容的 LLM API Key)

### 3.2 配置说明
在环境变量中设置 API Key：
```bash
export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxx
```
或者在 IDE 的 Run Configuration 中设置。

### 3.3 启动项目
```bash
./mvnw spring-boot:run
```

## 4. API 文档 (API Documentation)

### 4.1 智能对话接口 (流式)
- **URL**: `/three-stage/stream`
- **Method**: `GET`
- **Description**: 集成 RAG 和工具调用的混合对话接口，返回流式文本 (Server-Sent Events 格式)。
- **Params**:
    - `msg` (Required): 用户输入的消息
- **Example**:
  ```
  GET /three-stage/stream?msg=帮我查下iPhone 15的库存
  ```

### 4.2 标准响应结构 (Standard Response)
非流式接口统一采用以下 JSON 结构：
```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "traceId": "7b3h9d8s..."
}
```

## 5. 项目结构 (Project Structure)
```
src/main/java/org/zerolg/aidemo2/
├── aspect/          # AOP 切面 (参数矫正、日志)
├── common/          # 通用模块 (ApiResponse, ResultCode)
├── config/          # 配置类 (AI Config, ToolRegistry)
├── controller/      # 控制器层
├── exception/       # 全局异常处理
├── service/         # 业务逻辑与 AI 服务
└── AiDemo2Application.java
```

## 6. 开发规范 (Development Standards)
- **代码风格**: 遵循阿里巴巴 Java 开发手册。
- **分支管理**: Main 为稳定分支，Feature 分支开发新功能。
- **提交规范**: `feat: 增加新功能`, `fix: 修复bug`, `docs: 更新文档`。

| **P2** | **架构拆分** | ✅ 已完成 | 拆分 `RagService` 和 `PromptManager` |
| **P2** | **测试覆盖** | ⏳ 待开始 | 补充 AI 组件的单元测试与集成测试 |

---
**Maintainer**: Yucheng Guan
**License**: MIT
