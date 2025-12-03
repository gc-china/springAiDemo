package org.zerolg.aidemo2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.zerolg.aidemo2.model.IngestionStatus;
import org.zerolg.aidemo2.model.IngestionTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库摄入服务 (Producer)
 * 负责接收文件、保存、初始化状态并发送任务到 Stream
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final String STREAM_KEY = "ingestion:stream";
    private static final String STATUS_KEY_PREFIX = "ingestion:status:";
    private static final String UPLOAD_DIR = "uploads";

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate; // 用于操作 Hash 状态
    private final ObjectMapper objectMapper;

    public KnowledgeIngestionService(RedisTemplate<String, Object> redisTemplate,
                                     StringRedisTemplate stringRedisTemplate,
                                     ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        
        // 确保上传目录存在
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            logger.error("无法创建上传目录", e);
        }
    }

    /**
     * 提交摄入任务
     */
    public String submitTask(MultipartFile file) throws IOException {
        String ingestionId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String mimeType = file.getContentType();

        // 1. 保存文件到本地
        Path filePath = Paths.get(UPLOAD_DIR, ingestionId + "_" + originalFilename);
        file.transferTo(filePath.toFile());
        logger.info("文件已保存: {}", filePath);

        // 2. 初始化 Redis 状态
        String statusKey = STATUS_KEY_PREFIX + ingestionId;
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", IngestionStatus.PENDING.name());
        statusMap.put("progress", "0");
        statusMap.put("message", "Waiting for processing");
        statusMap.put("fileName", originalFilename);
        
        stringRedisTemplate.opsForHash().putAll(statusKey, statusMap);
        // 设置过期时间 (例如 24 小时)
        stringRedisTemplate.expire(statusKey, java.time.Duration.ofHours(24));

        // 3. 发送任务到 Redis Stream
        IngestionTask task = new IngestionTask(ingestionId, filePath.toString(), originalFilename, mimeType);
        
        // 将 Task 对象转换为 Map 以便存储到 Stream
        Map<String, Object> taskMap = objectMapper.convertValue(task, Map.class);
        // 注意：Stream 的 Record 值通常需要是 String，这里简单起见，我们将整个 JSON 作为 payload 字段
        // 或者使用 StringRedisTemplate 发送 JSON 字符串
        
        RecordId recordId = redisTemplate.opsForStream().add(StreamRecords.newRecord()
                .ofObject(task) // 使用 RedisTemplate 的序列化器
                .withStreamKey(STREAM_KEY));

        logger.info("任务已提交到 Stream: ingestionId={}, recordId={}", ingestionId, recordId);

        return ingestionId;
    }

    /**
     * 查询任务状态
     */
    public Map<Object, Object> getStatus(String ingestionId) {
        String statusKey = STATUS_KEY_PREFIX + ingestionId;
        return stringRedisTemplate.opsForHash().entries(statusKey);
    }
    
    /**
     * 更新任务状态 (供 Consumer 使用)
     */
    public void updateStatus(String ingestionId, IngestionStatus status, int progress, String message) {
        String statusKey = STATUS_KEY_PREFIX + ingestionId;
        Map<String, String> updates = new HashMap<>();
        updates.put("status", status.name());
        updates.put("progress", String.valueOf(progress));
        if (message != null) {
            updates.put("message", message);
        }
        stringRedisTemplate.opsForHash().putAll(statusKey, updates);
    }
}
