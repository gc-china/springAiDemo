package org.zerolg.aidemo2.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.model.SessionMessage;
import org.zerolg.aidemo2.properties.SessionProperties;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI 服务
 * 
 * 核心职责：
 * 1. 处理用户查询
 * 2. 管理会话上下文（使用 Redis 会话存储）
 * 3. 集成 RAG 检索
 * 4. 调用 LLM 生成回复
 * 
 * 会话管理策略：
 * - 使用 SessionMemoryService 管理会话历史
 * - 按 token 预算选择上下文（滑动窗口）
 * - 自动保存用户输入和 AI 回复
 * 
 * @author zerolg
 */
@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final ChatClient chatClient;
    private final RagService ragService;
    private final SessionMemoryService sessionMemoryService;
    private final SessionProperties sessionProperties;
    private final VerifierService verifierService; // 新增：幻觉验证服务
    private final ObjectMapper objectMapper;
    private final String[] availableTools;
    @Value("classpath:/static/rag-enhanced-prompt.st")
    private Resource ragEnhancedPromptResource;



    /**
     * 处理用户查询（支持多轮对话）
     * 
     * 完整流程：
     * 1. 检查会话是否存在，不存在则创建
     * 2. 保存用户消息到 Redis
     * 3. 从 Redis 获取历史消息（按 token 限制）
     * 4. RAG 检索相关文档
     * 5. 构建 Prompt（系统提示 + RAG 上下文 + 历史消息 + 当前问题）
     * 6. 调用 LLM 生成回复（流式）
     * 7. 保存 AI 回复到 Redis
     */
    public AiService(
            ChatClient chatClient, // 使用 Builder 以支持默认工具
            RagService ragService,
            SessionMemoryService sessionMemoryService,
            VerifierService verifierService,
            SessionProperties sessionProperties,
            ObjectMapper objectMapper,
            List<String> availableToolNames) {

        this.availableTools = availableToolNames.toArray(new String[0]);
        // 自动挂载工具
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.sessionMemoryService = sessionMemoryService;
        this.sessionProperties = sessionProperties;
        this.verifierService = verifierService;
        this.objectMapper = objectMapper;

        logger.info("AiService 初始化完成, 加载工具: {}", availableToolNames);
    }

    /**
     * 处理用户查询
     * * 保留了原有的会话管理逻辑：
     * 1. Check Session -> 2. Save User Msg -> 3. Get History
     * 新增了：
     * 4. Hybrid RAG -> 5. Stream -> 6. Verify
     * * @return Flux<ServerSentEvent<String>> 为了支持验证结果事件，升级了返回类型
     */
    public Flux<ServerSentEvent<String>> processQuery(String chatId, String msg) {
        logger.info("开始处理查询: chatId={}, msg={}", chatId, msg);

        // ==================== 1. 会话管理 (保留原有逻辑) ====================
        if (!sessionMemoryService.sessionExists(chatId)) {
            logger.info("会话不存在，创建新会话: chatId={}", chatId);
            sessionMemoryService.createSession(chatId, "default-user");
        }

        // ==================== 2. 保存用户消息 (保留原有逻辑) ====================
        int userTokens = estimateTokens(msg);
        SessionMessage userMessage = SessionMessage.createUserMessage(msg, userTokens)
                .withMetadata("userId", "default-user")
                .withMetadata("source", "web");

        // 关键点：在生成前就保存用户消息
        sessionMemoryService.saveMessage(chatId, userMessage);
        logger.debug("用户消息已保存: messageId={}, tokens={}", userMessage.id(), userTokens);

        // ==================== 3. 获取历史消息 (保留原有逻辑) ====================
        int maxHistoryTokens = sessionProperties.getMaxPromptTokens() - userTokens - 1000;
        List<SessionMessage> historyMessages = sessionMemoryService.getMessagesByTokenLimit(
                chatId,
                maxHistoryTokens
        );

        // ==================== 4. 混合检索 (升级为 Hybrid RAG) ====================
        // 使用 retrieveAndRerank 替代旧的 retrieve
        return ragService.retrieveAndRerank(msg)
                .flatMapMany(finalDocuments -> {

                    // ==================== 5. 构建 Prompt (逻辑不变) ====================
                    String ragContext = finalDocuments.stream()
                            .map(Document::getFormattedContent)
                            .collect(Collectors.joining("\n\n"));

                    PromptTemplate systemPromptTemplate = new PromptTemplate(ragEnhancedPromptResource);
                    String systemText = systemPromptTemplate.render(Map.of(
                            "context", ragContext.isEmpty() ? "暂无相关背景知识。" : ragContext
                    ));

                    List<Message> messages = historyMessages.stream()
                            .map(this::convertToSpringAiMessage)
                            .collect(Collectors.toList());
                    messages.add(new UserMessage(msg));

                    // ==================== 6. 调用 LLM & 流式响应 ====================
                    StringBuilder fullResponse = new StringBuilder();

                    return chatClient.prompt()
                            .system(systemText)
                            .messages(messages)
                            .toolNames(availableTools) // 已在构造函数中配置默认工具
                            .stream()
                            .content()
                            .map(chunk -> {
                                fullResponse.append(chunk);
                                // 包装为 SSE 消息事件
                                return ServerSentEvent.builder(chunk)
                                        .event("message")
                                        .build();
                            })
                            // ==================== 7. 保存 AI 回复 (保留原有逻辑) ====================
                            .doOnComplete(() -> {
                                String response = fullResponse.toString();
                                int assistantTokens = estimateTokens(response);
                                SessionMessage assistantMessage = SessionMessage.createAssistantMessage(
                                        response,
                                        assistantTokens
                                );
                                sessionMemoryService.saveMessage(chatId, assistantMessage);
                                logger.info("AI 回复已保存: tokens={}", assistantTokens);
                            })
                            // ==================== 8. 幻觉验证 (新增功能) ====================
                            .concatWith(Mono.defer(() -> {
                                // 流结束后，触发验证
                                return verifierService.verify(msg, finalDocuments, fullResponse.toString())
                                        .map(result -> {
                                            try {
                                                String json = objectMapper.writeValueAsString(result);
                                                // 发送验证结果事件
                                                return ServerSentEvent.builder(json)
                                                        .event("verification")
                                                        .build();
                                            } catch (JsonProcessingException e) {
                                                return ServerSentEvent.<String>builder().build();
                                            }
                                        });
                            }));
                });
    }

    /**
     * 将 SessionMessage 转换为 Spring AI 的 Message
     * 
     * 为什么需要转换：
     * - SessionMessage 是我们自定义的存储格式
     * - Spring AI 需要 Message 接口的实现
     * - 转换后才能传递给 ChatClient
     */
    private Message convertToSpringAiMessage(SessionMessage sessionMessage) {
        String role = sessionMessage.role();
        String content = sessionMessage.content();
        
        // 根据角色创建不同类型的 Message
        return switch (role) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            // system 和 tool 消息暂时转换为 UserMessage
            // 如果需要更精确的处理，可以使用 SystemMessage 和 ToolResponseMessage
            default -> new UserMessage(content);
        };
    }

    /**
     * 估算文本的 token 数量
     * 
     * 简化算法：
     * - 中文字符：1.5 字符 ≈ 1 token
     * - 英文单词：4 字符 ≈ 1 token
     * - 混合文本：取平均值
     * 
     * 为什么是估算：
     * - 精确计算需要使用 tokenizer（如 tiktoken）
     * - tokenizer 依赖模型，不同模型的 tokenizer 不同
     * - 估算足够用于滑动窗口策略
     * 
     * 优化建议：
     * - 可以集成 tiktoken 库进行精确计算
     * - 可以缓存计算结果（如果文本不变）
     * 
     * @param text 文本内容
     * @return 估算的 token 数量
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // 统计中文字符数量
        long chineseChars = text.chars()
                .filter(c -> c >= 0x4E00 && c <= 0x9FA5)
                .count();
        
        // 统计其他字符数量
        long otherChars = text.length() - chineseChars;
        
        // 中文：1.5 字符/token，英文：4 字符/token
        int tokens = (int) (chineseChars / 1.5 + otherChars / 4.0);
        
        // 至少 1 个 token
        return Math.max(1, tokens);
    }


}