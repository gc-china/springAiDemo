package org.zerolg.aidemo2.service.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.model.IngestionStatus;
import org.zerolg.aidemo2.model.IngestionTask;
import org.zerolg.aidemo2.service.KnowledgeBaseService;
import org.zerolg.aidemo2.service.KnowledgeIngestionService;
import org.zerolg.aidemo2.service.TikaDocumentParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class IngestionConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(IngestionConsumer.class);

    private final TikaDocumentParser parser;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private static final String STREAM_KEY = "ingestion:stream";
    private static final String GROUP_NAME = "ingestion-worker-group";
    private final StringRedisTemplate redisTemplate;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    @Autowired
    public IngestionConsumer(TikaDocumentParser parser,
                             KnowledgeBaseService knowledgeBaseService,
                             KnowledgeIngestionService ingestionService,
                             ObjectMapper objectMapper,
                             StringRedisTemplate redisTemplate,
                             @Qualifier("ingestionContainer") StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        this.parser = parser;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.container = container;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeAndStartContainer() {
        try {
            // 步骤 1: 检查并清理
            Boolean hasKey = redisTemplate.hasKey(STREAM_KEY);
            if (Boolean.TRUE.equals(hasKey) && !Objects.equals(redisTemplate.type(STREAM_KEY), DataType.STREAM)) {
                logger.warn("Key '{}' 存在但类型错误，将删除。", STREAM_KEY);
                redisTemplate.delete(STREAM_KEY);
                hasKey = false;
            }

            // 步骤 2: 创建 Stream
            if (Boolean.FALSE.equals(hasKey)) {
                redisTemplate.opsForStream().add(STREAM_KEY, Map.of("init", "stream_created"));
                logger.info("Stream '{}' 已创建。", STREAM_KEY);
            }

            // 步骤 3: 创建消费者组
            try {
                redisTemplate.opsForStream().createGroup(STREAM_KEY, GROUP_NAME);
                logger.info("消费者组 '{}' 已创建。", GROUP_NAME);
            } catch (Exception e) {
                if (isBusyGroupException(e)) {
                    logger.info("消费者组 '{}' 已存在。", GROUP_NAME);
                } else {
                    throw e;
                }
            }

            // 步骤 4: 启动容器
            if (!container.isRunning()) {
                container.start();
                logger.info("IngestionContainer 已启动。");
            }

        } catch (Exception e) {
            logger.error("初始化并启动 IngestionContainer 失败。", e);
        }
    }

    private boolean isBusyGroupException(Throwable e) {
        if (e == null) return false;
        if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) return true;
        return isBusyGroupException(e.getCause());
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        logger.info("收到摄入任务: {}", message.getId());
        
        IngestionTask task = null;
        try {
            Map<String, String> value = message.getValue();

            if (value.containsKey("init")) {
                redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, message.getId());
                return;
            }

            task = objectMapper.convertValue(value, IngestionTask.class);
            String ingestionId = task.ingestionId();
            
            logger.info("开始处理摄入任务: id={}, file={}", ingestionId, task.fileName());
            
            ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 10, "正在解析文档...");
            
            String content = parser.parseDocument(task.filePath());
            ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 40, "文档解析完成，开始切片...");
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "upload");
            metadata.put("mime_type", task.mimeType());
            
            knowledgeBaseService.ingest(task.fileName(), content, metadata);
            
            ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 90, "向量化完成，写入数据库...");
            
            ingestionService.updateStatus(ingestionId, IngestionStatus.COMPLETED, 100, "处理成功");
            logger.info("摄入任务完成: id={}", ingestionId);

            redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, message.getId());
            
        } catch (Exception e) {
            logger.error("摄入任务失败: {}", e.getMessage(), e);
            if (task != null) {
                ingestionService.updateStatus(
                    task.ingestionId(), 
                    IngestionStatus.FAILED, 
                    0, 
                    "处理失败: " + e.getMessage()
                );
            }
        }
    }
}
