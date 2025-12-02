package org.zerolg.aidemo2.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final ChatClient chatClient;
    private final RagService ragService;
    private final String[] availableTools; // 所有可用工具

    @Value("classpath:/static/rag-enhanced-prompt.st")
    private Resource ragEnhancedPromptResource;

    public AiService(ChatClient chatClient, RagService ragService, List<String> availableToolNames) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.availableTools = availableToolNames.toArray(new String[0]);
        logger.info("AiService 初始化完成,加载了 {} 个工具: {}", availableTools.length, String.join(", ", availableTools));
    }

    /**
     * 处理用户查询 (支持多轮对话)
     * 
     * 流程:
     * 1. RAG 检索与重排序 (由 RagService 处理)
     * 2. 构建 Prompt - 将上下文注入到 System Message
     * 3. 调用 AI - 使用工具增强的对话，并附带历史记忆
     * 
     * @param chatId 会话 ID (用于隔离不同用户的上下文)
     * @param msg 用户查询内容
     * @return 流式响应
     */
    public Flux<String> processQuery(String chatId, String msg) {
        logger.debug("开始处理查询, chatId: {}, msg: {}", chatId, msg);
        
        // 1. 委托 RagService 进行检索和重排序
        return ragService.retrieveAndRerank(msg)
            .flatMapMany(finalDocuments -> {
                // 2. 构建 Prompt
                String context = finalDocuments.stream()
                        .map(Document::getFormattedContent)
                        .collect(Collectors.joining("\n\n"));
                
                PromptTemplate systemPromptTemplate = new PromptTemplate(ragEnhancedPromptResource);
                String systemText = systemPromptTemplate.render(Map.of(
                        "context", context.isEmpty() ? "无" : context
                ));
                
                logger.debug("使用 {} 个工具进行查询", availableTools.length);

                // 3. 调用 AI (流式)
                // 使用 advisors 注入 ChatMemory
                return chatClient.prompt()
                        .system(systemText)
                        .user(msg)
                        .advisors(a -> a
                                .param("chat_memory_conversation_id", chatId)
                                .param("chat_memory_response_size", 10) // 记忆保留最近 10 条
                        )
                        .toolNames(availableTools)
                        .stream()
                        .content();
            });
    }
}