// AI配置类：配置AI聊天客户端和记忆功能

package org.zerolg.aidemo2.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AI配置类
 * <p>
 * 功能说明：
 * 1. 配置AI聊天客户端（ChatClient）
 * 2. 配置聊天记忆功能，让AI能记住对话历史
 * 3. 集成可用的工具列表
 * <p>
 * 架构设计：
 * - ChatClient：AI对话的核心客户端，负责与AI模型通信
 * - ChatMemory：对话记忆管理，存储历史消息
 * - MessageChatMemoryAdvisor：记忆顾问，自动管理对话上下文
 *
 * @author zerolg
 */
@Configuration
public class AiConfig {

    /**
     * 配置AI聊天客户端
     *
     * ChatClient是Spring AI的核心组件，提供以下功能：
     * 1. 与AI模型（如通义千问）进行对话
     * 2. 管理对话上下文和历史记录
     * 3. 集成各种顾问（Advisor）增强功能
     * 4. 支持工具调用（Function Calling）
     *
     * @param chatClientBuilder Spring AI提供的ChatClient构建器
     * @param availableToolNames 可用的工具名称列表（由ToolRegistry提供）
     * @return 配置好的ChatClient实例
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, List<String> availableToolNames) {
        return chatClientBuilder
                // 添加消息记忆顾问：让AI能记住之前的对话内容
                // MessageChatMemoryAdvisor会自动管理对话历史，确保上下文连贯性
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory()).build())
                .build();
    }

    /**
     * 配置聊天记忆功能
     *
     * ChatMemory负责存储和管理对话历史：
     * 1. 保存用户和AI的历史消息
     * 2. 在新对话时提供相关的历史上下文
     * 3. 控制记忆窗口大小，避免上下文过长
     *
     * 使用MessageWindowChatMemory的原因：
     * - 滑动窗口机制：只保留最近N条消息，避免上下文过长
     * - 内存存储：适合开发和测试环境
     * - 自动管理：无需手动清理过期消息
     *
     * 生产环境建议：
     * - 使用Redis等持久化存储替代InMemoryChatMemoryRepository
     * - 根据业务需求调整maxMessages大小
     * - 考虑实现自定义的ChatMemoryRepository
     *
     * @return 配置好的ChatMemory实例
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                // 使用内存存储库：简单但不持久化，重启后丢失
                // 生产环境建议使用RedisChatMemoryRepository等持久化方案
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                // 最大消息数：只保留最近10条消息
                // 这个数值需要平衡上下文完整性和性能：
                // - 太小：AI可能忘记重要信息
                // - 太大：消耗更多token，响应变慢
                .maxMessages(10)
                .build();
    }

}
