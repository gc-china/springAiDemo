package org.zerolg.aidemo2.service.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.model.IngestionStatus;
import org.zerolg.aidemo2.model.IngestionTask;
import org.zerolg.aidemo2.service.KnowledgeBaseService;
import org.zerolg.aidemo2.service.KnowledgeIngestionService;
import org.zerolg.aidemo2.service.TikaDocumentParser;

import java.util.HashMap;
import java.util.Map;

/**
 * 文档摄入消费者 (Consumer)
 * 监听 Redis Stream 中的摄入任务，执行 ETL 流程
 */
@Component
public class IngestionConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(IngestionConsumer.class);

    private final TikaDocumentParser parser;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public IngestionConsumer(TikaDocumentParser parser,
                            KnowledgeBaseService knowledgeBaseService,
                            KnowledgeIngestionService ingestionService,
                            ObjectMapper objectMapper) {
        this.parser = parser;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        logger.info("收到摄入任务: {}", message.getId());
        
        IngestionTask task = null;
        try {
            // 1. 解析任务
            Map<String, String> value = message.getValue();
            task = objectMapper.convertValue(value, IngestionTask.class);
            String ingestionId = task.ingestionId();
            
            logger.info("开始处理摄入任务: id={}, file={}", ingestionId, task.fileName());
            
            // 2. 更新状态：处理中
            ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 10, "正在解析文档...");
            
            // 3. 解析文档 (Extract)
            String content = parser.parseDocument(task.filePath());
            ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 40, "文档解析完成，开始切片...");
            
            // 4. 切分并向量化 (Transform & Load)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "upload");
            metadata.put("mime_type", task.mimeType());
            
            knowledgeBaseService.ingest(task.fileName(), content, metadata);
            
            ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 90, "向量化完成，写入数据库...");
            
            // 5. 完成
            ingestionService.updateStatus(ingestionId, IngestionStatus.COMPLETED, 100, "处理成功");
            logger.info("摄入任务完成: id={}", ingestionId);
            
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
