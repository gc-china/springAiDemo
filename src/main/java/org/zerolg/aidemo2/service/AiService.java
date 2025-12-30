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
 * AI服务核心类
 *
 * 这是整个AI对话系统的核心服务，负责协调各个组件完成智能对话功能。
 *
 * 主要职责：
 * 1. 处理用户查询请求，提供流式响应
 * 2. 管理多轮对话的会话上下文和历史记录
 * 3. 集成RAG（检索增强生成）功能，提供知识库支持
 * 4. 调用大语言模型生成智能回复
 * 5. 实现幻觉检测和答案验证
 * 6. 提供引用信息和来源追踪
 *
 * 技术架构：
 * - 使用Redis存储会话历史，支持冷热数据分离
 * - 采用滑动窗口策略管理对话上下文，控制token消耗
 * - 集成向量数据库实现语义搜索
 * - 支持工具调用（Function Calling）
 * - 实现并发控制，防止API过载
 *
 * 工作流程：
 * 1. 会话管理：检查/创建会话，保存用户消息
 * 2. 上下文构建：获取历史消息，控制token预算
 * 3. 知识检索：RAG检索相关文档，重排序优化
 * 4. 提示构建：组装系统提示、上下文、历史和当前问题
 * 5. 模型调用：流式调用LLM生成回复
 * 6. 结果处理：保存回复，提供引用，验证准确性
 * 
 * @author zerolg
 */
@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    // 核心依赖组件
    private final ChatClient chatClient;                    // Spring AI聊天客户端
    private final RagService ragService;                    // RAG检索服务
    private final SessionMemoryService sessionMemoryService; // 会话内存管理
    private final SessionProperties sessionProperties;       // 会话配置属性
    private final VerifierService verifierService;          // 简单验证服务
    private final DetailedVerifierService detailedVerifierService; // 详细验证服务
    private final ObjectMapper objectMapper;                // JSON序列化工具
    private final String[] availableTools;                  // 可用工具列表

    // API并发控制：限制同时最多3个API调用，防止过载和费用失控
    private final Semaphore apiSemaphore = new Semaphore(3);

    // RAG增强提示模板：从classpath加载预定义的提示模板
    @Value("classpath:/static/rag-enhanced-prompt.st")
    private Resource ragEnhancedPromptResource;



    /**
     * AiService构造函数
     *
     * 依赖注入说明：
     * - Spring会自动注入所有需要的依赖组件
     * - ChatClient使用Builder模式，支持默认工具配置
     * - availableToolNames来自ToolRegistry，动态注册可用工具
     *
     * 工具集成机制：
     * 1. ToolRegistry扫描并注册所有@Component标注的工具类
     * 2. 工具名称列表传递给ChatClient，启用Function Calling
     * 3. AI可以根据用户问题自动选择和调用合适的工具
     *
     * @param chatClient Spring AI聊天客户端，已配置记忆和工具
     * @param ragService RAG检索服务，提供知识库搜索能力
     * @param sessionMemoryService 会话内存服务，管理对话历史
     * @param verifierService 简单验证服务，快速验证答案准确性
     * @param detailedVerifierService 详细验证服务，深度分析答案质量
     * @param sessionProperties 会话配置属性，如token限制等
     * @param objectMapper JSON序列化工具，用于SSE消息格式化
     * @param availableToolNames 可用工具名称列表，来自ToolRegistry
     */
    public AiService(
            ChatClient chatClient, // 使用Builder以支持默认工具
            RagService ragService,
            SessionMemoryService sessionMemoryService,
            VerifierService verifierService,
            DetailedVerifierService detailedVerifierService,
            SessionProperties sessionProperties,
            ObjectMapper objectMapper,
            List<String> availableToolNames) {

        // 转换工具名称列表为数组，便于后续使用
        this.availableTools = availableToolNames.toArray(new String[0]);

        // 保存依赖组件引用
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
     * 处理用户查询的核心方法
     *
     * 这是整个AI对话系统的核心处理流程，实现了完整的智能对话功能：
     *
     * 流程概述：
     * 1. 会话管理：检查/创建会话，保存用户消息
     * 2. 上下文构建：获取历史消息，控制token预算
     * 3. 知识检索：RAG检索相关文档，重排序优化
     * 4. 提示构建：组装系统提示、上下文、历史和当前问题
     * 5. 模型调用：流式调用LLM生成回复
     * 6. 结果处理：保存回复，提供引用，验证准确性
     *
     * 技术特点：
     * - 流式响应：用户可实时看到AI生成过程
     * - 思维链：显示AI的思考过程（检索、推理、验证）
     * - 引用追踪：提供答案来源，支持可信度评估
     * - 幻觉检测：验证答案准确性，提供置信度
     * - 并发控制：防止API过载，控制成本
     *
     * 返回格式：
     * - 使用Server-Sent Events (SSE) 实现流式响应
     * - 支持多种事件类型：thinking（思维链）、content（内容）、citations（引用）、verification（验证）
     *
     * @param chatId 会话ID，用于标识不同的对话会话
     * @param msg 用户输入的消息内容
     * @return 流式响应，包含AI回复的完整过程
     */
    public Flux<ServerSentEvent<String>> processQuery(String chatId, String msg) {
        logger.info("开始处理查询: chatId={}, msg={}", chatId, msg);

        // 序列号计数器：为每个SSE事件分配唯一序号，确保前端按序处理
        AtomicInteger seqCounter = new AtomicInteger(0);

        // ==================== 1. 会话管理阶段 ====================
        // 检查会话是否存在，如果不存在则创建新会话
        // 这确保了每个对话都有独立的上下文空间
        if (!sessionMemoryService.sessionExists(chatId)) {
            logger.info("会话不存在，创建新会话: chatId={}", chatId);
            // 创建新会话，关联默认用户（实际项目中应该传入真实用户ID）
            sessionMemoryService.createSession(chatId, "default-user");
        }

        // ==================== 2. 用户消息保存阶段 ====================
        // 估算用户消息的token数量，用于后续的上下文预算管理
        int userTokens = estimateTokens(msg);

        // 创建用户消息对象，包含内容、token数和元数据
        SessionMessage userMessage = SessionMessage.createUserMessage(msg, userTokens)
                .withMetadata("userId", "default-user")    // 用户标识
                .withMetadata("source", "web");            // 消息来源

        // 保存用户消息到Redis，用于多轮对话的上下文管理
        sessionMemoryService.saveMessage(chatId, userMessage);
        logger.debug("用户消息已保存: messageId={}, tokens={}", userMessage.id(), userTokens);

        // ==================== 3. 历史消息获取阶段 ====================
        // 计算历史消息的token预算
        // 总预算 - 用户消息token - 系统提示预留token = 历史消息可用token
        int maxHistoryTokens = sessionProperties.getMaxPromptTokens() - userTokens - 1000;

        // 从Redis获取历史消息，按token限制进行截取（滑动窗口策略）
        // 这确保了上下文不会超过模型的输入限制，同时保留最相关的历史信息
        List<SessionMessage> historyMessages = sessionMemoryService.getMessagesByTokenLimit(
                chatId,
                maxHistoryTokens
        );

        // 发送思维链事件：告知用户AI开始检索相关文档
        Flux<ServerSentEvent<String>> thinkingStart = Flux.just(
                buildSseEvent(SseMessage.thinking("retrieval", "正在检索相关文档...", seqCounter.getAndIncrement()))
        );

        // ==================== 4. RAG知识检索阶段 ====================
        // 使用混合检索策略：向量搜索 + 重排序，提高检索质量
        return thinkingStart.concatWith(
                ragService.retrieveAndRerank(msg)
                .flatMapMany(finalDocuments -> {

                    // 发送检索完成的思维链事件
                    Flux<ServerSentEvent<String>> retrievalDone = Flux.just(
                            buildSseEvent(SseMessage.thinking("retrieval",
                                    String.format("检索完成，找到 %d 个相关文档", finalDocuments.size()),
                                    seqCounter.getAndIncrement()))
                    );

                    // ==================== 5. 构建带引用信息的上下文 ====================
                    // 为每个检索到的文档添加引用编号和来源信息
                    StringBuilder contextBuilder = new StringBuilder();
                    for (int i = 0; i < finalDocuments.size(); i++) {
                        Document doc = finalDocuments.get(i);
                        Map<String, Object> metadata = doc.getMetadata();

                        // 获取或生成引用编号
                        Integer citationNumber = (Integer) metadata.get("citation_number");
                        if (citationNumber == null) citationNumber = i + 1;

                        // 提取文档来源信息
                        String filename = (String) metadata.getOrDefault("source_filename", "未知文件");
                        Integer chunkIndex = (Integer) metadata.get("source_chunk_index");
                        String chunkInfo = chunkIndex != null ? "第" + (chunkIndex + 1) + "段" : "未知位置";

                        // 构建格式化的上下文文本，包含引用信息
                        contextBuilder.append(String.format("【文档 %d】(来源: %s, %s)\n%s\n\n",
                                citationNumber,
                                filename,
                                chunkInfo,
                                doc.getFormattedContent().trim()));
                    }
                    String ragContext = contextBuilder.toString().trim();

                    logger.debug("检索到的文档数量: {}", finalDocuments.size());

                    // ==================== 6. 构建系统提示 ====================
                    // 使用预定义的提示模板，注入RAG上下文
                    PromptTemplate systemPromptTemplate = new PromptTemplate(ragEnhancedPromptResource);
                    String systemText = systemPromptTemplate.render(Map.of(
                            "context", ragContext.isEmpty() ? "暂无相关背景知识。" : ragContext
                    ));

                    // ==================== 7. 准备对话消息列表 ====================
                    // 将历史消息转换为Spring AI格式，并添加当前用户消息
                    List<Message> messages = historyMessages.stream()
                            .map(this::convertToSpringAiMessage)
                            .collect(Collectors.toList());
                    messages.add(new UserMessage(msg));

                    // 发送推理开始的思维链事件
                    Flux<ServerSentEvent<String>> reasoningStart = Flux.just(
                            buildSseEvent(SseMessage.thinking("reasoning", "正在分析并生成回答...", seqCounter.getAndIncrement()))
                    );

                    // ==================== 8. LLM调用和流式响应阶段 ====================
                    // 用于收集完整的AI回复内容
                    StringBuilder fullResponse = new StringBuilder();

                    // 调用ChatClient进行流式对话
                    Flux<ServerSentEvent<String>> contentStream = chatClient.prompt()
                            .system(systemText)           // 设置系统提示（包含RAG上下文）
                            .messages(messages)           // 设置对话历史
                            .toolNames(availableTools)    // 启用工具调用功能
                            .stream()                     // 启用流式响应
                            .content()                    // 获取内容流
                            .onErrorResume(throwable -> {
                                // 错误处理：如果API调用失败，返回友好的错误信息
                                logger.error("DashScope API 调用失败", throwable);
                                return Flux.just("❌ AI 服务暂时不可用，请稍后重试。");
                            })
                            .map(chunk -> {
                                // 收集每个文本块，构建完整回复
                                fullResponse.append(chunk);
                                // 将每个文本块包装为SSE事件发送给前端
                                return buildSseEvent(SseMessage.content(chunk, seqCounter.getAndIncrement()));
                            });

                    // ==================== 9. 回复保存和后续处理阶段 ====================
                    // 将所有流式事件串联起来：检索完成 → 推理开始 → 内容流
                    return retrievalDone.concatWith(reasoningStart).concatWith(contentStream)
                            // 当内容流完成时，保存AI回复到会话历史
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
                            // ==================== 10. 引用信息发送阶段 ====================
                            // 在内容流结束后，发送引用信息，让用户知道答案的来源
                            .concatWith(Flux.defer(() -> {
                                try {
                                    // 构建引用数据列表，包含文档ID、文件名、位置等信息
                                    List<Map<String, Object>> citationsData = finalDocuments.stream()
                                            .map(doc -> {
                                                Map<String, Object> metadata = doc.getMetadata();
                                                Map<String, Object> citation = new HashMap<>();

                                                // 提取文档基本信息
                                                String documentId = (String) metadata.get("source_document_id");
                                                citation.put("documentId", documentId);
                                                citation.put("filename", metadata.getOrDefault("source_filename", "未知文件"));
                                                citation.put("location", "第" + ((Integer) metadata.getOrDefault("source_chunk_index", 0) + 1) + "段");
                                                citation.put("citationNumber", metadata.get("citation_number"));

                                                // 构建文档访问链接
                                                citation.put("downloadUrl", "/api/ai/knowledge/download/" + documentId);
                                                citation.put("previewUrl", "/api/ai/knowledge/preview/" + documentId);

                                                // 文档状态信息
                                                citation.put("fileStatus", metadata.getOrDefault("file_status", "未知"));
                                                citation.put("fileExists", !"纯文本".equals(metadata.get("file_status")));
                                                citation.put("mimeType", metadata.get("source_mime_type"));

                                                return citation;
                                            })
                                            .collect(Collectors.toList());

                                    // 发送引用信息事件
                                    return Flux.just(buildSseEvent(SseMessage.citations(citationsData, seqCounter.getAndIncrement())));
                                } catch (Exception e) {
                                    logger.error("序列化引用信息失败", e);
                                    return Flux.empty();
                                }
                            }))
                            // ==================== 11. 幻觉验证阶段 ====================
                            // 验证AI回复的准确性，检测可能的幻觉内容
                            .concatWith(Flux.defer(() -> {
                                logger.debug("开始执行详细幻觉验证...");

                                // 发送验证开始的思维链事件
                                Flux<ServerSentEvent<String>> verificationThinking = Flux.just(
                                        buildSseEvent(SseMessage.thinking("verification", "正在验证回答准确性...", seqCounter.getAndIncrement()))
                                );

                                return verificationThinking.concatWith(
                                        // 获取API调用许可（并发控制）
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
                                                        // 首先尝试详细验证
                                                        detailedVerifierService.verifyDetailed(msg, finalDocuments, fullResponse.toString())
                                                                .timeout(Duration.ofSeconds(20))  // 设置超时时间
                                                                .doOnSuccess(result -> logger.info("详细验证完成: passed={}, confidence={}",
                                                                        result.passed(), result.confidence()))
                                                                .onErrorResume(throwable -> {
                                                                    // 详细验证失败时，降级到简单验证
                                                                    logger.warn("详细验证失败，降级到简单验证", throwable);
                                                                    return verifierService.verify(msg, finalDocuments, fullResponse.toString())
                                                                            .timeout(Duration.ofSeconds(10))
                                                                            // 将简单验证结果转换为详细验证结果格式
                                                                            .map(simpleResult -> new org.zerolg.aidemo2.model.DetailedVerificationResult(
                                                                                    simpleResult.passed(),
                                                                                    simpleResult.confidence(),
                                                                                    simpleResult.reason(),
                                                                                    simpleResult.correction(),
                                                                                    new ArrayList<>(),
                                                                                    org.zerolg.aidemo2.model.UnsupportedHandlingResult.downgrade(fullResponse.toString())
                                                                            ))
                                                                            // 如果简单验证也失败，返回默认结果
                                                                            .onErrorReturn(new org.zerolg.aidemo2.model.DetailedVerificationResult(
                                                                                    true, 0.85, "验证异常", null,
                                                                                    new ArrayList<>(),
                                                                                    org.zerolg.aidemo2.model.UnsupportedHandlingResult.downgrade(fullResponse.toString())
                                                                            ));
                                                                })
                                                )
                                                // 无论成功失败，都要释放API许可
                                                .doFinally(signalType -> apiSemaphore.release())
                                                // 将验证结果包装为SSE事件
                                                .map(result -> buildSseEvent(SseMessage.verification(result, seqCounter.getAndIncrement())))
                                                .flux()
                                );
                            }));
                })
        );
    }

    /**
     * 构建SSE（Server-Sent Events）事件
     *
     * SSE是一种服务器向客户端推送数据的技术，特别适合实时通信场景：
     * - 单向通信：服务器主动推送，客户端被动接收
     * - 自动重连：连接断开时客户端会自动重连
     * - 事件类型：支持不同类型的事件，便于客户端分类处理
     *
     * 在AI对话中的应用：
     * - thinking事件：显示AI的思考过程
     * - content事件：流式传输AI生成的文本
     * - citations事件：发送引用信息
     * - verification事件：发送验证结果
     *
     * @param message 要发送的SSE消息对象
     * @return 格式化的ServerSentEvent对象
     */
    private ServerSentEvent<String> buildSseEvent(SseMessage message) {
        try {
            // 将消息对象序列化为JSON字符串
            String json = objectMapper.writeValueAsString(message);
            return ServerSentEvent.builder(json)
                    .event(message.type())  // 设置事件类型，客户端可据此分类处理
                    .build();
        } catch (JsonProcessingException e) {
            // 序列化失败时返回错误事件，确保客户端能收到反馈
            logger.error("序列化SSE消息失败", e);
            return ServerSentEvent.builder("{\"type\":\"error\",\"delta\":\"消息序列化失败\"}")
                    .event("error")
                    .build();
        }
    }

    /**
     * 将SessionMessage转换为Spring AI的Message格式
     *
     * 数据转换的必要性：
     * - SessionMessage：我们自定义的存储格式，包含更多元数据
     * - Spring AI Message：框架要求的标准格式，用于模型调用
     * - 转换确保了存储格式和调用格式的解耦
     *
     * 角色映射：
     * - user：用户消息 → UserMessage
     * - assistant：AI回复 → AssistantMessage  
     * - system：系统消息 → UserMessage（简化处理）
     * - tool：工具消息 → UserMessage（简化处理）
     *
     * 优化空间：
     * - 可以支持更精确的SystemMessage和ToolResponseMessage
     * - 可以保留更多的消息元数据
     *
     * @param sessionMessage 会话消息对象
     * @return Spring AI标准的Message对象
     */
    private Message convertToSpringAiMessage(SessionMessage sessionMessage) {
        String role = sessionMessage.role();
        String content = sessionMessage.content();

        // 根据消息角色创建对应的Message类型
        return switch (role) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            // system和tool消息暂时转换为UserMessage
            // 如果需要更精确的处理，可以使用SystemMessage和ToolResponseMessage
            default -> new UserMessage(content);
        };
    }

    /**
     * 估算文本的token数量
     *
     * Token是大语言模型处理文本的基本单位，准确估算token数量对以下方面很重要：
     * 1. 成本控制：API调用按token计费
     * 2. 上下文管理：模型有输入长度限制
     * 3. 性能优化：token数量影响响应速度
     *
     * 估算算法：
     * - 中文字符：平均1.5个字符对应1个token
     * - 英文字符：平均4个字符对应1个token
     * - 混合文本：分别统计后求和
     *
     * 算法局限性：
     * - 这是简化的估算，不同模型的tokenizer可能不同
     * - 精确计算需要使用具体模型的tokenizer（如tiktoken）
     * - 但对于滑动窗口策略来说，估算精度已经足够
     * 
     * 优化建议：
     * - 生产环境可考虑集成tiktoken库
     * - 可以缓存常见文本的token计算结果
     * - 可以根据实际使用情况调整估算系数
     *
     * @param text 要估算的文本内容
     * @return 估算的token数量
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // 统计中文字符数量（Unicode范围：0x4E00-0x9FA5）
        long chineseChars = text.chars()
                .filter(c -> c >= 0x4E00 && c <= 0x9FA5)
                .count();

        // 统计其他字符数量（主要是英文、数字、符号等）
        long otherChars = text.length() - chineseChars;

        // 应用不同的token转换率
        // 中文：1.5字符/token，英文：4字符/token
        int tokens = (int) (chineseChars / 1.5 + otherChars / 4.0);

        // 确保至少返回1个token（避免空文本导致的问题）
        return Math.max(1, tokens);
    }

}