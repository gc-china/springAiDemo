package org.zerolg.aidemo2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.zerolg.aidemo2.model.IngestionStatus;
import org.zerolg.aidemo2.constant.RedisKeys;
import org.zerolg.aidemo2.model.IngestionTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库摄入服务 (Producer)
 * 负责接收文件、保存、初始化状态并发送任务到 Stream
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final String STATUS_KEY_PREFIX = "ingestion:status:";

    // 内存中保存 SSE 连接 (Key: ingestionId)
    // 注意：如果是多实例部署，这里需要改为 Redis Pub/Sub 机制
    private final Map<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate; // 用于操作 Hash 状态
    private final ObjectMapper objectMapper;
    // 从配置文件读取上传目录
    @Value("${file.upload-dir}")
    private String uploadDir;

    public KnowledgeIngestionService(RedisTemplate<String, Object> redisTemplate,
                                     StringRedisTemplate stringRedisTemplate,
                                     ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交摄入任务
     */
    public String submitTask(MultipartFile file) throws IOException {
        String ingestionId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String mimeType = file.getContentType();

        // 1. 保存文件到本地
        Path uploadPath = Paths.get(uploadDir);
        // 确保上传目录存在
        if (Files.notExists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(ingestionId + "_" + originalFilename);
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
        stringRedisTemplate.expire(statusKey, Duration.ofHours(24));

        // 3. 发送任务到 Redis Stream
        IngestionTask task = new IngestionTask(ingestionId, filePath.toString(), originalFilename, mimeType);

        // 修复 2: 封装消息格式，Consumer 期望的是 key 为 "payload" 的 JSON 字符串
        // 使用 stringRedisTemplate 确保序列化为纯字符串，避免乱码或类型问题
        String taskJson = objectMapper.writeValueAsString(task);
        RecordId recordId = stringRedisTemplate.opsForStream()
                .add(RedisKeys.STREAM_DOCUMENT_INGESTION, Map.of("payload", taskJson));

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
     * 创建 SSE 连接用于实时接收进度
     *
     * @param ingestionId 任务ID
     * @return SseEmitter 实例
     */
    public SseEmitter subscribeStatus(String ingestionId) {
        // 设置一个较长的超时时间，例如 10 分钟
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        // 连接建立时，先发送一次当前状态
        try {
            emitter.send(SseEmitter.event().name("progress").data(getStatus(ingestionId)));
        } catch (IOException e) {
            logger.warn("SSE首次发送状态失败: {}", ingestionId, e);
        }

        // 注册 Emitter
        sseEmitters.put(ingestionId, emitter);

        // 定义连接结束后的清理逻辑
        Runnable cleanup = () -> sseEmitters.remove(ingestionId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError((e) -> cleanup.run());

        logger.info("SSE连接已建立: ingestionId={}", ingestionId);
        return emitter;
    }

    /**
     * 更新任务状态 (供 Consumer 使用)
     */
    public void updateStatus(String ingestionId, IngestionStatus status, int progress, String message) {
        String statusKey = STATUS_KEY_PREFIX + ingestionId;

        // 1. 更新 Redis 中的持久化状态
        stringRedisTemplate.opsForHash().put(statusKey, "status", status.name());
        stringRedisTemplate.opsForHash().put(statusKey, "progress", String.valueOf(progress));
        stringRedisTemplate.opsForHash().put(statusKey, "message", message);

        // 2. 通过 SSE 推送实时状态
        SseEmitter emitter = sseEmitters.get(ingestionId);
        if (emitter != null) {
            try {
                Map<String, Object> eventData = Map.of("status", status.name(), "progress", progress, "message", message);
                emitter.send(SseEmitter.event().name("progress").data(eventData));

                // 如果任务结束（完成或失败），主动关闭连接
                if (status == IngestionStatus.COMPLETED || status == IngestionStatus.FAILED) {
                    emitter.complete();
                }
            } catch (IOException e) {
                logger.warn("SSE推送失败，移除连接: {}", ingestionId);
                sseEmitters.remove(ingestionId); // 推送失败时也清理掉
            }
        }
    }
}
