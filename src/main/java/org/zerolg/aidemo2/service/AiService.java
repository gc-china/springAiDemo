package org.zerolg.aidemo2.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.model.SessionMessage;
import org.zerolg.aidemo2.properties.SessionProperties;

import reactor.core.publisher.Flux;

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
    private final String[] availableTools; // 所有可用工具

    @Value("classpath:/static/rag-enhanced-prompt.st")
    private Resource ragEnhancedPromptResource;

    /**
     * 构造函数注入依赖
     * 
     * @param chatClient          LLM 客户端
     * @param ragService          RAG 服务
     * @param sessionMemoryService 会话记忆服务（Redis 实现）
     * @param sessionProperties   会话配置
     * @param availableToolNames  可用工具列表
     */
    public AiService(
            ChatClient chatClient, 
            RagService ragService,
            SessionMemoryService sessionMemoryService,
            SessionProperties sessionProperties,
            List<String> availableToolNames) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.sessionMemoryService = sessionMemoryService;
        this.sessionProperties = sessionProperties;
        this.availableTools = availableToolNames.toArray(new String[0]);
        
        logger.info("AiService 初始化完成");
        logger.info("  - 加载工具数量: {}", availableTools.length);
        logger.info("  - 工具列表: {}", String.join(", ", availableTools));
        logger.info("  - 会话配置: {}", sessionProperties);
    }

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
     * 
     * 为什么使用流式响应：
     * - 用户体验好：实时看到回复，不用等待
     * - 降低延迟感：即使总时间相同，流式响应感觉更快
     * - 支持取消：用户可以中途停止生成
     * 
     * @param chatId 会话 ID（用于隔离不同用户的上下文）
     * @param msg    用户查询内容
     * @return 流式响应（Flux<String>）
     */
    public Flux<String> processQuery(String chatId, String msg) {
        logger.info("开始处理查询: chatId={}, msg={}", chatId, msg);
        
        // ==================== 1. 会话管理 ====================
        
        // 检查会话是否存在，不存在则创建
        if (!sessionMemoryService.sessionExists(chatId)) {
            logger.info("会话不存在，创建新会话: chatId={}", chatId);
            // TODO: 从请求上下文或 JWT 中获取真实的 userId
            sessionMemoryService.createSession(chatId, "default-user");
        }
        
        // 保存用户消息
        // 简单估算 token 数：中文约 1.5 字符/token，英文约 4 字符/token
        int userTokens = estimateTokens(msg);
        SessionMessage userMessage = SessionMessage.createUserMessage(msg, userTokens)
                .withMetadata("userId", "default-user")
                .withMetadata("source", "web");
        
        sessionMemoryService.saveMessage(chatId, userMessage);
        logger.debug("用户消息已保存: messageId={}, tokens={}", userMessage.id(), userTokens);
        
        // ==================== 2. 获取历史消息 ====================
        
        // 按 token 预算获取历史消息（滑动窗口策略）
        // 预留一部分 token 给当前问题和 AI 回复
        int maxHistoryTokens = sessionProperties.getMaxPromptTokens() - userTokens - 1000;
        List<SessionMessage> historyMessages = sessionMemoryService.getMessagesByTokenLimit(
                chatId, 
                maxHistoryTokens
        );
        
        logger.debug("获取历史消息: count={}, totalTokens={}", 
                historyMessages.size(),
                historyMessages.stream().mapToInt(SessionMessage::tokens).sum());
        
        // ==================== 3. RAG 检索 ====================
        
        // 委托 RagService 进行检索和重排序
        return ragService.retrieveAndRerank(msg)
            .flatMapMany(finalDocuments -> {
                
                // ==================== 4. 构建 Prompt ====================
                
                // 4.1 构建 RAG 上下文
                String ragContext = finalDocuments.stream()
                        .map(Document::getFormattedContent)
                        .collect(Collectors.joining("\n\n"));
                
                // 4.2 构建系统提示词（包含 RAG 上下文）
                PromptTemplate systemPromptTemplate = new PromptTemplate(ragEnhancedPromptResource);
                String systemText = systemPromptTemplate.render(Map.of(
                        "context", ragContext.isEmpty() ? "无" : ragContext
                ));
                
                // 4.3 构建历史消息列表（转换为 Spring AI 的 Message 格式）
                List<Message> messages = historyMessages.stream()
                        .map(this::convertToSpringAiMessage)
                        .collect(Collectors.toList());
                
                // 4.4 添加当前用户消息
                messages.add(new UserMessage(msg));
                
                logger.debug("Prompt 构建完成:");
                logger.debug("  - 系统提示词长度: {} 字符", systemText.length());
                logger.debug("  - RAG 文档数量: {}", finalDocuments.size());
                logger.debug("  - 历史消息数量: {}", historyMessages.size());
                logger.debug("  - 可用工具数量: {}", availableTools.length);
                
                // ==================== 5. 调用 LLM（流式） ====================
                
                // 注意：这里不再使用 advisors 的 ChatMemory
                // 而是手动管理历史消息，更灵活可控
                Flux<String> responseFlux = chatClient.prompt()
                        .system(systemText)
                        .messages(messages)  // 传入历史消息
                        .toolNames(availableTools)
                        .stream()
                        .content();
                
                // ==================== 6. 保存 AI 回复 ====================
                
                // 使用 StringBuilder 收集完整回复
                StringBuilder fullResponse = new StringBuilder();
                
                return responseFlux
                        // 收集每个流式片段
                        .doOnNext(chunk -> {
                            fullResponse.append(chunk);
                            logger.trace("收到流式片段: {}", chunk);
                        })
                        // 流结束时保存完整回复
                        .doOnComplete(() -> {
                            String response = fullResponse.toString();
                            int assistantTokens = estimateTokens(response);
                            
                            SessionMessage assistantMessage = SessionMessage.createAssistantMessage(
                                    response, 
                                    assistantTokens
                            );
                            
                            sessionMemoryService.saveMessage(chatId, assistantMessage);
                            
                            logger.info("AI 回复已保存: messageId={}, tokens={}, length={}", 
                                    assistantMessage.id(), 
                                    assistantTokens,
                                    response.length());
                        })
                        // 错误处理
                        .doOnError(error -> {
                            logger.error("处理查询时发生错误: chatId={}", chatId, error);
                        });
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