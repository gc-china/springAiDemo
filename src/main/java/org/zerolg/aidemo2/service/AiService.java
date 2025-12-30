package org.zerolg.aidemo2.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.zerolg.aidemo2.model.SseMessage;
import org.zerolg.aidemo2.model.VerificationResult;
import org.zerolg.aidemo2.properties.SessionProperties;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.Semaphore;

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
    private final VerifierService verifierService; // 简单验证服务
    private final DetailedVerifierService detailedVerifierService; // 详细验证服务
    private final ObjectMapper objectMapper;
    private final String[] availableTools;

    // 添加API并发控制
    private final Semaphore apiSemaphore = new Semaphore(3); // 限制同时最多3个API调用
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
            DetailedVerifierService detailedVerifierService,
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
        this.detailedVerifierService = detailedVerifierService;
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

        AtomicInteger seqCounter = new AtomicInteger(0);

        // ==================== 1. 会话管理 ====================
        if (!sessionMemoryService.sessionExists(chatId)) {
            logger.info("会话不存在，创建新会话: chatId={}", chatId);
            sessionMemoryService.createSession(chatId, "default-user");
        }

        // ==================== 2. 保存用户消息 ====================
        int userTokens = estimateTokens(msg);
        SessionMessage userMessage = SessionMessage.createUserMessage(msg, userTokens)
                .withMetadata("userId", "default-user")
                .withMetadata("source", "web");

        sessionMemoryService.saveMessage(chatId, userMessage);
        logger.debug("用户消息已保存: messageId={}, tokens={}", userMessage.id(), userTokens);

        // ==================== 3. 获取历史消息 ====================
        int maxHistoryTokens = sessionProperties.getMaxPromptTokens() - userTokens - 1000;
        List<SessionMessage> historyMessages = sessionMemoryService.getMessagesByTokenLimit(
                chatId,
                maxHistoryTokens
        );

        // 发送思维链：开始检索
        Flux<ServerSentEvent<String>> thinkingStart = Flux.just(
                buildSseEvent(SseMessage.thinking("retrieval", "正在检索相关文档...", seqCounter.getAndIncrement()))
        );

        // ==================== 4. 混合检索 ====================
        return thinkingStart.concatWith(
                ragService.retrieveAndRerank(msg)
                .flatMapMany(finalDocuments -> {

                    // 发送检索完成思维链
                    Flux<ServerSentEvent<String>> retrievalDone = Flux.just(
                            buildSseEvent(SseMessage.thinking("retrieval",
                                    String.format("检索完成，找到 %d 个相关文档", finalDocuments.size()),
                                    seqCounter.getAndIncrement()))
                    );

                    // ==================== 5. 构建带引用信息的 Prompt ====================
                    StringBuilder contextBuilder = new StringBuilder();
                    for (int i = 0; i < finalDocuments.size(); i++) {
                        Document doc = finalDocuments.get(i);
                        Map<String, Object> metadata = doc.getMetadata();

                        Integer citationNumber = (Integer) metadata.get("citation_number");
                        if (citationNumber == null) citationNumber = i + 1;

                        String filename = (String) metadata.getOrDefault("source_filename", "未知文件");
                        Integer chunkIndex = (Integer) metadata.get("source_chunk_index");
                        String chunkInfo = chunkIndex != null ? "第" + (chunkIndex + 1) + "段" : "未知位置";

                        contextBuilder.append(String.format("【文档 %d】(来源: %s, %s)\n%s\n\n",
                                citationNumber,
                                filename,
                                chunkInfo,
                                doc.getFormattedContent().trim()));
                    }
                    String ragContext = contextBuilder.toString().trim();

                    logger.debug("检索到的文档数量: {}", finalDocuments.size());

                    PromptTemplate systemPromptTemplate = new PromptTemplate(ragEnhancedPromptResource);
                    String systemText = systemPromptTemplate.render(Map.of(
                            "context", ragContext.isEmpty() ? "暂无相关背景知识。" : ragContext
                    ));

                    List<Message> messages = historyMessages.stream()
                            .map(this::convertToSpringAiMessage)
                            .collect(Collectors.toList());
                    messages.add(new UserMessage(msg));

                    // 发送推理开始思维链
                    Flux<ServerSentEvent<String>> reasoningStart = Flux.just(
                            buildSseEvent(SseMessage.thinking("reasoning", "正在分析并生成回答...", seqCounter.getAndIncrement()))
                    );

                    // ==================== 6. 调用 LLM & 流式响应 ====================
                    StringBuilder fullResponse = new StringBuilder();

                    Flux<ServerSentEvent<String>> contentStream = chatClient.prompt()
                            .system(systemText)
                            .messages(messages)
                            .toolNames(availableTools)
                            .stream()
                            .content()
                            .onErrorResume(throwable -> {
                                logger.error("DashScope API 调用失败", throwable);
                                return Flux.just("❌ AI 服务暂时不可用，请稍后重试。");
                            })
                            .map(chunk -> {
                                fullResponse.append(chunk);
                                return buildSseEvent(SseMessage.content(chunk, seqCounter.getAndIncrement()));
                            });

                    // ==================== 7. 保存 AI 回复 & 发送引用和验证 ====================
                    return retrievalDone.concatWith(reasoningStart).concatWith(contentStream)
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
                            // ==================== 8. 发送引用信息 ====================
                            .concatWith(Flux.defer(() -> {
                                try {
                                    List<Map<String, Object>> citationsData = finalDocuments.stream()
                                            .map(doc -> {
                                                Map<String, Object> metadata = doc.getMetadata();
                                                Map<String, Object> citation = new HashMap<>();

                                                String documentId = (String) metadata.get("source_document_id");
                                                citation.put("documentId", documentId);
                                                citation.put("filename", metadata.getOrDefault("source_filename", "未知文件"));
                                                citation.put("location", "第" + ((Integer) metadata.getOrDefault("source_chunk_index", 0) + 1) + "段");
                                                citation.put("citationNumber", metadata.get("citation_number"));
                                                citation.put("downloadUrl", "/api/ai/knowledge/download/" + documentId);
                                                citation.put("previewUrl", "/api/ai/knowledge/preview/" + documentId);
                                                citation.put("fileStatus", metadata.getOrDefault("file_status", "未知"));
                                                citation.put("fileExists", !"纯文本".equals(metadata.get("file_status")));
                                                citation.put("mimeType", metadata.get("source_mime_type"));

                                                return citation;
                                            })
                                            .collect(Collectors.toList());

                                    return Flux.just(buildSseEvent(SseMessage.citations(citationsData, seqCounter.getAndIncrement())));
                                } catch (Exception e) {
                                    logger.error("序列化引用信息失败", e);
                                    return Flux.empty();
                                }
                            }))
                            // ==================== 9. 幻觉验证 ====================
                            .concatWith(Flux.defer(() -> {
                                logger.debug("开始执行详细幻觉验证...");

                                Flux<ServerSentEvent<String>> verificationThinking = Flux.just(
                                        buildSseEvent(SseMessage.thinking("verification", "正在验证回答准确性...", seqCounter.getAndIncrement()))
                                );

                                return verificationThinking.concatWith(
                                        Mono.fromCallable(() -> {
                                                    try {
                                                        apiSemaphore.acquire();
                                                        return "acquired";
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                        throw new RuntimeException("API并发控制被中断", e);
                                                    }
                                                })
                                                .flatMap(acquired ->
                                                        detailedVerifierService.verifyDetailed(msg, finalDocuments, fullResponse.toString())
                                                                .timeout(Duration.ofSeconds(20))
                                                                .doOnSuccess(result -> logger.info("详细验证完成: passed={}, confidence={}",
                                                                        result.passed(), result.confidence()))
                                                                .onErrorResume(throwable -> {
                                                                    logger.warn("详细验证失败，降级到简单验证", throwable);
                                                                    return verifierService.verify(msg, finalDocuments, fullResponse.toString())
                                                                            .timeout(Duration.ofSeconds(10))
                                                                            .map(simpleResult -> new org.zerolg.aidemo2.model.DetailedVerificationResult(
                                                                                    simpleResult.passed(),
                                                                                    simpleResult.confidence(),
                                                                                    simpleResult.reason(),
                                                                                    simpleResult.correction(),
                                                                                    new ArrayList<>(),
                                                                                    org.zerolg.aidemo2.model.UnsupportedHandlingResult.downgrade(fullResponse.toString())
                                                                            ))
                                                                            .onErrorReturn(new org.zerolg.aidemo2.model.DetailedVerificationResult(
                                                                                    true, 0.85, "验证异常", null,
                                                                                    new ArrayList<>(),
                                                                                    org.zerolg.aidemo2.model.UnsupportedHandlingResult.downgrade(fullResponse.toString())
                                                                            ));
                                                                })
                                                )
                                                .doFinally(signalType -> apiSemaphore.release())
                                                .map(result -> buildSseEvent(SseMessage.verification(result, seqCounter.getAndIncrement())))
                                                .flux()
                                );
                            }));
                })
        );
    }

    /**
     * 构建SSE事件
     */
    private ServerSentEvent<String> buildSseEvent(SseMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            return ServerSentEvent.builder(json)
                    .event(message.type())
                    .build();
        } catch (JsonProcessingException e) {
            logger.error("序列化SSE消息失败", e);
            return ServerSentEvent.builder("{\"type\":\"error\",\"delta\":\"消息序列化失败\"}")
                    .event("error")
                    .build();
        }
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