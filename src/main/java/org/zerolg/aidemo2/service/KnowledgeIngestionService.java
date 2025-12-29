package org.zerolg.aidemo2.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import org.zerolg.aidemo2.model.IngestionStatus;
import org.zerolg.aidemo2.model.IngestionTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KnowledgeIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final String STREAM_KEY = "ingestion:stream";
    private static final String STATUS_KEY_PREFIX = "ingestion:status:";

    @Value("${file.upload-dir:ragFiles}")
    private String uploadDir;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // 用于存储活跃的 SSE 连接
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public KnowledgeIngestionService(StringRedisTemplate stringRedisTemplate,
                                     ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void initUploadDirectory() {
        try {
            // 确保目录创建在配置指定的路径下
            Files.createDirectories(Paths.get(uploadDir).toAbsolutePath());
            logger.info("上传目录已创建: {}", Paths.get(uploadDir).toAbsolutePath());
        } catch (IOException e) {
            logger.error("无法创建上传目录: {}", uploadDir, e);
        }
    }

    public String submitTask(MultipartFile file) throws IOException {
        String ingestionId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String mimeType = file.getContentType();

        // 1. 保存文件 (✅ 修复：使用配置的上传目录)
        Path filePath = Paths.get(uploadDir, ingestionId + "_" + originalFilename).toAbsolutePath();
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
        stringRedisTemplate.expire(statusKey, java.time.Duration.ofHours(24));

        // 3. 发送任务到 Redis Stream
        IngestionTask task = new IngestionTask(ingestionId, filePath.toString(), originalFilename, mimeType);
        Map<String, String> taskMap = objectMapper.convertValue(task, new TypeReference<Map<String, String>>() {
        });

        RecordId recordId = stringRedisTemplate.opsForStream().add(STREAM_KEY, taskMap);
        logger.info("任务已提交到 Stream: ingestionId={}, recordId={}", ingestionId, recordId);

        return ingestionId;
    }

    public Map<Object, Object> getStatus(String ingestionId) {
        String statusKey = STATUS_KEY_PREFIX + ingestionId;
        return stringRedisTemplate.opsForHash().entries(statusKey);
    }

    // 订阅状态流
    public SseEmitter subscribeStatus(String ingestionId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5分钟超时
        emitters.put(ingestionId, emitter);

        Runnable cleanup = () -> emitters.remove(ingestionId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError((e) -> cleanup.run());

        return emitter;
    }

    // 更新状态并推送到 SSE
    public void updateStatus(String ingestionId, IngestionStatus status, int progress, String message) {
        // 1. 更新 Redis
        String statusKey = STATUS_KEY_PREFIX + ingestionId;
        Map<String, String> updates = new HashMap<>();
        updates.put("status", status.name());
        updates.put("progress", String.valueOf(progress));
        if (message != null) {
            updates.put("message", message);
        }
        stringRedisTemplate.opsForHash().putAll(statusKey, updates);

        // 2. 推送 SSE
        SseEmitter emitter = emitters.get(ingestionId);
        if (emitter != null) {
            try {
                Map<String, Object> event = new HashMap<>();
                event.put("status", status.name());
                event.put("progress", progress);
                event.put("message", message);

                emitter.send(event);

                if (status == IngestionStatus.COMPLETED || status == IngestionStatus.FAILED) {
                    emitter.complete();
                    emitters.remove(ingestionId);
                }
            } catch (IOException e) {
                emitters.remove(ingestionId);
            }
        }
    }
}