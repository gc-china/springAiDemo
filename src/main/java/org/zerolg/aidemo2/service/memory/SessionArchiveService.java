package org.zerolg.aidemo2.service.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zerolg.aidemo2.constant.RedisKeys;
import org.zerolg.aidemo2.entity.SessionArchive;
import org.zerolg.aidemo2.entity.SessionArchiveIndex;
import org.zerolg.aidemo2.mapper.SessionArchiveIndexMapper;
import org.zerolg.aidemo2.mapper.SessionArchiveMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话归档服务
 * 负责将 Redis 中的冷数据持久化到 PostgreSQL
 */
@Service
@RequiredArgsConstructor
public class SessionArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(SessionArchiveService.class);
    private final SessionArchiveIndexMapper indexMapper;
    private final SessionArchiveMapper archiveMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    // 定义监控指标：归档成功计数器
    private Counter archiveSuccessCounter;
    private Counter archiveErrorCounter;

    // 初始化指标
    public void initMetrics() {
        this.archiveSuccessCounter = meterRegistry.counter("aidemo.session.archive.success");
        this.archiveErrorCounter = meterRegistry.counter("aidemo.session.archive.error");
    }

    /**
     * 执行归档操作 (通常由 Redis Stream Consumer 调用)
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param contentJson    完整的会话历史 JSON
     * @param msgCount       消息数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void archiveSession(String conversationId, String userId, String contentJson, int msgCount) {
        if (archiveSuccessCounter == null) initMetrics();

        try {
            logger.info(">>> 开始归档会话: {}", conversationId);

            // 0. 解析 JSON 获取真实元数据 (Token数、摘要、时间)
            int calculatedTokens = 0;
            String summary = "新会话"; // 默认摘要
            LocalDateTime startTime = LocalDateTime.now(); // 默认为当前，若能解析则更新
            Map contentObj = null; // 用于存储转换后的对象

            try {
                JsonNode rootNode = objectMapper.readTree(contentJson);
                if (rootNode.isArray()) {
                    // 计算 Total Tokens
                    for (JsonNode node : rootNode) {
                        calculatedTokens += node.path("tokens").asInt(0);
                    }

                    // 生成摘要：提取第一条用户消息的前 64 个字符
                    // 真实场景中，如果需要高质量摘要，建议在归档前异步调用 LLM 生成并存入 Redis metadata，此处直接读取即可
                    // 这里采用 "提取式摘要" 策略，既高效又符合大多数列表展示需求，且避免在事务中调用 LLM
                    for (JsonNode node : rootNode) {
                        if ("user".equalsIgnoreCase(node.path("role").asText())) {
                            String text = node.path("text").asText();
                            if (!text.isBlank()) {
                                summary = text.length() > 64 ? text.substring(0, 64) + "..." : text;
                                break;
                            }
                        }
                    }
                    // TODO: 如果 metadata 中包含 timestamp，可在此处解析 startTime
                }

                // 【关键修复】将 JSON 字符串转换为 Java Object (Map 或 List)，以便 JacksonTypeHandler 处理
                contentObj = objectMapper.readValue(contentJson, Map.class);

            } catch (Exception e) {
                logger.warn("解析会话内容JSON失败，使用默认值。ID: {}", conversationId, e);
                // 解析失败时的兜底策略：存入一个包含原始数据的 Map
                contentObj = Map.of("error", "parse_failed", "raw", contentJson);
            }
            // 1. 保存完整内容到 session_archives 表 (冷数据)
            SessionArchive archive = SessionArchive.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .payload(contentObj) // 修正字段名为 contentJson，并传入转换后的对象
                    .totalTokens(calculatedTokens)
                    .timestamp(Instant.now())
                    .createdAt(Instant.now())
                    .build();
            archiveMapper.insert(archive);

            // 2. 保存轻量级索引 (热数据)
            SessionArchiveIndex index = SessionArchiveIndex.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .messageCount(msgCount)
                    .summary(summary)
                    .totalTokens(calculatedTokens)
                    .startTime(startTime)
                    .lastActiveTime(LocalDateTime.now()) // 归档时刻即为最后活跃时刻(近似)
                    .archivedAt(LocalDateTime.now())
                    .build();

            indexMapper.insert(index);

            // 3. 更新监控指标
            archiveSuccessCounter.increment();
            logger.info("<<< 会话归档完成: {}", conversationId);

        } catch (Exception e) {
            logger.error("!!! 会话归档失败: {}", conversationId, e);
            archiveErrorCounter.increment();
            throw e; // 抛出异常以触发重试或进入 DLQ
        }
    }

    /**
     * 查询用户的历史会话列表 (分页)
     * 只查索引表，速度快
     */
    public Page<SessionArchiveIndex> getUserHistory(String userId, int page, int size) {
        Page<SessionArchiveIndex> pageParam = new Page<>(page, size);
        return indexMapper.selectPage(pageParam, new LambdaQueryWrapper<SessionArchiveIndex>()
                .eq(SessionArchiveIndex::getUserId, userId)
                .orderByDesc(SessionArchiveIndex::getLastActiveTime));
    }

    /**
     * 获取单个会话的完整详情
     * 查主表，加载大 JSON
     */
    public Optional<SessionArchive> getSessionDetail(String conversationId) {
        return Optional.ofNullable(archiveMapper.selectById(conversationId));
    }

    /**
     * 尝试激活/回捞会话
     * 场景：用户对一个已归档的会话发送了新消息
     * 逻辑：DB -> Redis -> Delete DB
     *
     * @param conversationId 会话ID
     * @return true=回捞成功, false=无需回捞(DB无记录)
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean reactivateSession(String conversationId) {
        // 1. 检查主归档表
        SessionArchive archive = archiveMapper.selectById(conversationId);
        if (archive == null) {
            return false; // 确实是新会话，或者 ID 不存在
        }

        logger.info(">>> 检测到归档会话 [{}], 正在执行回捞 (DB -> Redis)...", conversationId);

        try {
            Map<String, Object> payload = archive.getPayload();


            String listKey = RedisKeys.SESSION_MSG_PREFIX + conversationId;
            List<Map<String, Object>> messages = objectMapper.readValue(
                    objectMapper.writeValueAsString(payload),
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
            // 3. 写入 Redis
            for (Map<String, Object> msgMap : messages) {
                String msgJson = objectMapper.writeValueAsString(msgMap);
                redisTemplate.opsForList().rightPush(listKey, msgJson);
            }

            // 4. 恢复元数据 (UserId)
            String metaKey = RedisKeys.SESSION_META_PREFIX + conversationId;
            if (archive.getUserId() != null) {
                redisTemplate.opsForHash().put(metaKey, "userId", archive.getUserId());
            }

            // 5. 删除数据库中的冷数据 (维护 "数据要么在热区，要么在冷区" 的原则)
            archiveMapper.deleteById(conversationId);
            indexMapper.delete(new LambdaQueryWrapper<SessionArchiveIndex>()
                    .eq(SessionArchiveIndex::getConversationId, conversationId));

            logger.info("<<< 会话 [{}] 回捞成功，已转为热数据。", conversationId);
            return true;

        } catch (Exception e) {
            logger.error("!!! 会话 [{}] 回捞失败", conversationId, e);
            throw new RuntimeException("会话激活失败", e); // 抛出异常回滚事务
        }
    }
}