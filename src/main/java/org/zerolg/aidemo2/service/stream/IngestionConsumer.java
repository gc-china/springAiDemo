package org.zerolg.aidemo2.service.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.constant.RedisKeys;
import org.zerolg.aidemo2.model.IngestionStatus;
import org.zerolg.aidemo2.service.KnowledgeIngestionService;
import org.zerolg.aidemo2.service.KnowledgeBaseService;

import java.util.Map;
import java.util.HashMap;

/**
 * 文档 Ingestion 消费者 (ETL Worker)
 * 职责：监听文档上传事件 -> 文本切片 -> 向量化 -> 存入 PGVector
 */
@Service
public class IngestionConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(IngestionConsumer.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KnowledgeIngestionService ingestionService; // 注入 Service 用于更新状态
    private final KnowledgeBaseService knowledgeBaseService; // 注入核心业务 Service

    private static final String GROUP_NAME = "ingestion-worker-group";

    @Autowired
    public IngestionConsumer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                             KnowledgeIngestionService ingestionService,
                             KnowledgeBaseService knowledgeBaseService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String msgId = message.getId().toString();
        try {
            Map<String, String> value = message.getValue();
            String payloadJson = value.get("payload");

            if (payloadJson == null) {
                ack(msgId);
                return;
            }

            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);

            String filePath = (String) payload.get("filePath");
            String ingestionId = (String) payload.get("ingestionId");

            Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
            if (metadata == null) metadata = new HashMap<>();

            // 将所有业务逻辑委托给 KnowledgeBaseService
            knowledgeBaseService.ingestDocument(ingestionId, filePath, metadata);

            // 确认消息
            ack(msgId);

        } catch (Exception e) {
            logger.error("文档处理失败: {}", msgId, e);
            // 可以在这里增加失败状态的更新
            try {
                Map<String, Object> payload = objectMapper.readValue(message.getValue().get("payload"), Map.class);
                String ingestionId = (String) payload.get("ingestionId");
                ingestionService.updateStatus(ingestionId, IngestionStatus.FAILED, 0, "处理异常: " + e.getMessage());
            } catch (Exception ex) {
                logger.error("更新失败状态时再次发生异常", ex);
            }
        }
    }

    private void ack(String msgId) {
        redisTemplate.opsForStream().acknowledge(RedisKeys.STREAM_DOCUMENT_INGESTION, GROUP_NAME, msgId);
    }
}