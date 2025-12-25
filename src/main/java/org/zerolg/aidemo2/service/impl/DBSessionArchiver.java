package org.zerolg.aidemo2.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zerolg.aidemo2.entity.SessionArchive;
import org.zerolg.aidemo2.mapper.SessionArchiveMapper;
import org.zerolg.aidemo2.model.SessionEvent;
import org.zerolg.aidemo2.service.SessionArchiver;

import java.time.Instant;

/**
 * 基于数据库的会话归档服务实现
 * 
 * 作用：
 * 替代之前的 LoggingSessionArchiver，将会话事件持久化到 PostgreSQL 数据库。
 * 使用 MyBatis Plus 的 Mapper 进行数据插入。
 */
@Service
public class DBSessionArchiver implements SessionArchiver {

    private static final Logger logger = LoggerFactory.getLogger(DBSessionArchiver.class);

    private final SessionArchiveMapper sessionArchiveMapper;
    private final ObjectMapper objectMapper;

    public DBSessionArchiver(SessionArchiveMapper sessionArchiveMapper, ObjectMapper objectMapper) {
        this.sessionArchiveMapper = sessionArchiveMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 归档会话事件
     * 
     * @param event 需要归档的会话事件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(SessionEvent event) {
        try {
            logger.debug("开始归档事件: eventId={}", event.getEventId());

            // 1. 将 SessionEvent 模型转换为 SessionArchive 实体
            SessionArchive archive = new SessionArchive();

            // 修复: 之前代码 setConversationId 调用了两次，逻辑冲突。
            // 假设 conversationId 是主键，直接使用 event 中的会话ID
            archive.setConversationId(event.getConversationId());

            archive.setType(event.getType());

            // 处理 Payload: String -> Object (为了让 MyBatis TypeHandler 正确处理 JSONB)
            if (event.getPayload() != null) {
                archive.setPayload(event.getPayload());
            }

            archive.setTimestamp(event.getTimestamp());
            archive.setCreatedAt(Instant.now());

            // 2. 【关键】设置 UserId
            // 优先从 Event 中获取 (假设 SessionEvent 有 getUserId 方法)
            // String userId = event.getUserId(); 
            // 如果 Event 中没有，尝试从 Payload JSON 中解析 (兜底策略)
            String userId = "unknown";
            if (event.getPayload() != null) {
                userId = (String) event.getPayload().get("userId");
                // 假设 payload 是数组且第一条包含 userId，或者 payload 本身有 userId 字段
            }
            archive.setUserId(userId);

            // 3. 插入数据库
            sessionArchiveMapper.insert(archive);

            logger.info("事件归档成功: id={}, type={}", archive.getConversationId(), archive.getType());

        } catch (Exception e) {
            logger.error("事件归档失败: eventId={}", event.getEventId(), e);
            // 抛出异常以触发事务回滚 (如果有事务上下文) 或让上层调用者处理 (如 DLQ)
            throw new RuntimeException("归档失败", e);
        }
    }
}
