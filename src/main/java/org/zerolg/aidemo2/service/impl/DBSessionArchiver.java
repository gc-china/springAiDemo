package org.zerolg.aidemo2.service.impl;

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

    public DBSessionArchiver(SessionArchiveMapper sessionArchiveMapper) {
        this.sessionArchiveMapper = sessionArchiveMapper;
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
            // 如果 eventId 为空，生成一个新的 UUID (虽然通常 eventId 应该由生产者生成)
            archive.setId(event.getEventId() != null ? event.getEventId() : java.util.UUID.randomUUID().toString());
            archive.setConversationId(event.getConversationId());
            archive.setType(event.getType());
            archive.setPayload(event.getPayload()); // JacksonTypeHandler 会自动处理 Map -> JSON
            archive.setTimestamp(event.getTimestamp());
            archive.setCreatedAt(Instant.now());

            // 2. 插入数据库
            sessionArchiveMapper.insert(archive);

            logger.info("事件归档成功: id={}, type={}", archive.getId(), archive.getType());

        } catch (Exception e) {
            logger.error("事件归档失败: eventId={}", event.getEventId(), e);
            // 抛出异常以触发事务回滚 (如果有事务上下文) 或让上层调用者处理 (如 DLQ)
            throw new RuntimeException("归档失败", e);
        }
    }
}
