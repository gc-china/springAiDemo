package org.zerolg.aidemo2.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import org.springframework.core.io.Resource; // 导入 Resource
import org.springframework.beans.factory.annotation.Value; // 导入 @Value
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class AiController {
    // 注入 RAG 模板文件
    @Value("classpath:/static/rag-enhanced-prompt.st")
    private Resource ragEnhancedPromptResource;

    // 注入通用问答模板文件
    @Value("classpath:/static/general-prompt.st")
    private Resource generalPromptResource;
    @Autowired
    private ChatClient chatClient;
    private final VectorStore vectorStore;

    // 构造函数：移除 defaultFunctions，仅构建 ChatClient 实例
    public AiController( VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        // ❗ 修正：由于 defaultFunctions 报错，我们不进行全局注册，只构建客户端
    }

    /**
     * 最终优化的混合路由流式接口 (Tool Override + 动态工具注册)
     */
    @GetMapping("/three-stage/stream")
    public Flux<String> threeStageHybridChatStream(@RequestParam String msg) {

        String lowerCaseMsg = msg.toLowerCase(Locale.ROOT);
        // 意图判断：检查是否为工具调用关键字
        // 意图判断：添加新的关键字
        boolean isToolQuery = lowerCaseMsg.contains("库存")
                || lowerCaseMsg.contains("马桶")
                || lowerCaseMsg.contains("测试")
                || lowerCaseMsg.contains("用户") // 新增关键字
                || lowerCaseMsg.contains("工号");
  // 核心：创建 DashScope Options Builder
        // 注册工具的名称列表
        List<String> toolNames = List.of("getProductStock", "getUserInfo");
        // --- 路由逻辑：分三条路径 ---
        if (isToolQuery) {
            // --- 路径 A: 强制工具调用 (同步执行) ---
            System.out.println(">>> 🔧 路径 A: 检测到工具关键字，跳过 RAG 检索，同步执行工具调用。");

            // 关键：在 prompt() 链式调用中显式注册工具
            String finalAnswer = chatClient.prompt()
                    .user(msg).toolNames(toolNames.toArray(new String[0])) // 用户问题
                    .call() // 同步执行，完成多轮工具调用闭环
                    .content();

            // 将同步结果封装成流返回
            return Flux.just(finalAnswer);

        } else {
            // --- 路径 B/C: RAG 检索或通用问答 ---
            System.out.println(">>> ❓ 执行 RAG 检索...");

            // 2. RAG 检索 (仅在非工具查询时执行);
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(msg)
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);

            String context = documents.stream()
                    // 修正点 2: 使用明确的 lambda d -> d.getContent()
                    .map(Document::getFormattedContent)
                    .collect(Collectors.joining("\n\n"));

            if (!context.isEmpty()) {
                // --- 路径 B: RAG 命中 (非工具查询 + RAG 命中) ---
                System.out.println(">>> 📄 路径 B: 文档命中，执行 RAG 增强 (流式)。");

                Prompt finalPrompt = new PromptTemplate(ragEnhancedPromptResource).create(Map.of(
                        "context", context,
                        "question", msg
                ));

                // RAG 路径使用 stream()
                return chatClient.prompt(finalPrompt).stream().content();

            } else {
                // --- 路径 C: 通用问答 (非工具查询 + RAG 未命中) ---
                System.out.println(">>> 💬 路径 C: 无可用资料，执行通用问答 (流式)。");



                // 通用问答路径，使用 stream() 并注册工具作为兜底
                return chatClient.prompt()
                        .user(generalPromptResource)
                        .stream()
                        .content();
            }
        }
    }
}
