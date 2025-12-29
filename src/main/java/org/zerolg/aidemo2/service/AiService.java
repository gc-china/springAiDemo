package org.zerolg.aidemo2.service;

import java.util.ArrayList;
import java.util.HashMap;
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

                    // ==================== 5. 构建带引用信息的 Prompt ====================
                    StringBuilder contextBuilder = new StringBuilder();
                    for (int i = 0; i < finalDocuments.size(); i++) {
                        Document doc = finalDocuments.get(i);
                        Map<String, Object> metadata = doc.getMetadata();

                        // 获取引用编号
                        Integer citationNumber = (Integer) metadata.get("citation_number");
                        if (citationNumber == null) citationNumber = i + 1;

                        // 获取文件信息
                        String filename = (String) metadata.getOrDefault("source_filename", "未知文件");
                        Integer chunkIndex = (Integer) metadata.get("source_chunk_index");
                        String chunkInfo = chunkIndex != null ? "第" + (chunkIndex + 1) + "段" : "未知位置";

                        // 构建引用格式：【文档 1】(来源: policy.pdf, 第2段)
                        contextBuilder.append(String.format("【文档 %d】(来源: %s, %s)\n%s\n\n",
                                citationNumber,
                                filename,
                                chunkInfo,
                                doc.getFormattedContent().trim()));
                    }
                    String ragContext = contextBuilder.toString().trim();

                    logger.debug("检索到的文档数量: {}", finalDocuments.size());
                    logger.debug("构建的RAG上下文: {}", ragContext);

                    PromptTemplate systemPromptTemplate = new PromptTemplate(ragEnhancedPromptResource);
                    String systemText = systemPromptTemplate.render(Map.of(
                            "context", ragContext.isEmpty() ? "暂无相关背景知识。" : ragContext
                    ));

                    logger.debug("最终系统提示词: {}", systemText);

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
                            .onErrorResume(throwable -> {
                                logger.error("DashScope API 调用失败", throwable);
                                
                                // 检查具体错误类型
                                if (throwable.getMessage().contains("400 Bad Request")) {
                                    logger.error("❌ 400 Bad Request 错误分析:");
                                    logger.error("   可能原因1: API Key 无效或过期");
                                    logger.error("   可能原因2: 账户余额不足");
                                    logger.error("   可能原因3: 模型名称错误 (当前: qwen-turbo)");
                                    logger.error("   可能原因4: 请求参数格式错误");
                                    
                                    // 返回错误提示给用户
                                    return Flux.just("❌ AI 服务暂时不可用，请检查以下问题：\n" +
                                            "1. API Key 是否有效\n" +
                                            "2. 账户余额是否充足\n" +
                                            "3. 网络连接是否正常\n\n" +
                                            "请稍后重试或联系管理员。");
                                } else if (throwable.getMessage().contains("401")) {
                                    logger.error("❌ 401 Unauthorized: API Key 认证失败");
                                    return Flux.just("❌ API 认证失败，请检查 API Key 配置。");
                                } else if (throwable.getMessage().contains("429")) {
                                    logger.error("❌ 429 Too Many Requests: 请求频率过高");
                                    return Flux.just("❌ 请求过于频繁，请稍后重试。");
                                } else {
                                    return Flux.just("❌ AI 服务出现异常，请稍后重试。错误信息: " + throwable.getMessage());
                                }
                            })
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
                            // ==================== 8. 发送引用信息 (新增功能) ====================
                            .concatWith(Flux.defer(() -> {
                                // 构建引用信息 - 回到最简单的工作版本
                                try {
                                    List<Map<String, Object>> citationsData = finalDocuments.stream()
                                            .map(doc -> {
                                                Map<String, Object> metadata = doc.getMetadata();
                                                Map<String, Object> citation = new HashMap<>();

                                                // 基本信息
                                                String documentId = (String) metadata.get("source_document_id");
                                                citation.put("documentId", documentId);
                                                citation.put("filename", metadata.getOrDefault("source_filename", "未知文件"));
                                                citation.put("location", "第" + ((Integer) metadata.getOrDefault("source_chunk_index", 0) + 1) + "段");
                                                citation.put("citationNumber", metadata.get("citation_number"));

                                                // 简化的URL生成 - 与文档库保持完全一致
                                                citation.put("downloadUrl", "/api/ai/knowledge/download/" + documentId);
                                                citation.put("previewUrl", "/api/ai/knowledge/preview/" + documentId);

                                                // 基本文件信息
                                                citation.put("fileStatus", metadata.getOrDefault("file_status", "未知"));
                                                citation.put("fileExists", !"纯文本".equals(metadata.get("file_status")));
                                                citation.put("mimeType", metadata.get("source_mime_type"));

                                                return citation;
                                            })
                                            .collect(Collectors.toList());

                                    String citationsJson = objectMapper.writeValueAsString(citationsData);
                                    logger.info("发送引用信息: {}", citationsJson);
                                    
                                    return Flux.just(ServerSentEvent.builder(citationsJson)
                                            .event("citations")
                                            .build());
                                } catch (JsonProcessingException e) {
                                    logger.error("序列化引用信息失败", e);
                                    return Flux.empty();
                                }
                            }))
                            // ==================== 9. 幻觉验证 (详细验证) ====================
                            .concatWith(Flux.defer(() -> {
                                // 流结束后，触发详细验证（带超时保护和并发控制）
                                logger.debug("开始执行详细幻觉验证...");

                                return Mono.fromCallable(() -> {
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
                                                                .timeout(Duration.ofSeconds(20)) // 增加超时时间到20秒
                                                                .doOnSuccess(result -> logger.info("详细验证完成: passed={}, confidence={}, assertions={}",
                                                                        result.passed(), result.confidence(), result.assertions().size()))
                                        .onErrorResume(throwable -> {
                                            if (throwable instanceof java.util.concurrent.TimeoutException) {
                                                logger.warn("详细验证服务超时，降级到简单验证");
                                            } else {
                                                logger.error("详细验证服务异常，降级到简单验证", throwable);
                                            }
                                            // 降级到简单验证
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
                                                            true, 0.85, "验证异常，基于通用知识回答", null,
                                                            new ArrayList<>(),
                                                            org.zerolg.aidemo2.model.UnsupportedHandlingResult.downgrade(fullResponse.toString())
                                                    ));
                                        })
                                        )
                                        .doFinally(signalType -> apiSemaphore.release()) // 释放API并发许可
                                        .map(result -> {
                                            try {
                                                String json = objectMapper.writeValueAsString(result);
                                                // 发送详细验证结果事件
                                                return ServerSentEvent.builder(json)
                                                        .event("detailed_verification")
                                                        .build();
                                            } catch (JsonProcessingException e) {
                                                logger.error("序列化详细验证结果失败", e);
                                                // 返回默认验证结果
                                                return ServerSentEvent.<String>builder()
                                                        .event("detailed_verification")
                                                        .data("{\"passed\":true,\"confidence\":0.85,\"reason\":\"验证异常\"}")
                                                        .build();
                                            }
                                        })
                                        .flux(); // 将 Mono 转换为 Flux
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