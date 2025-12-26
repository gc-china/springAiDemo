package org.zerolg.aidemo2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.zerolg.aidemo2.service.KnowledgeIngestionService;
import org.zerolg.aidemo2.service.KnowledgeBaseService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai/knowledge") // 1. 统一基础路径
public class KnowledgeBaseController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeIngestionService ingestionService; // 2. 注入文件摄入服务
    private final VectorStore vectorStore;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeIngestionService ingestionService,
                                   VectorStore vectorStore) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.ingestionService = ingestionService;
        this.vectorStore = vectorStore;
    }

    /**
     * 摄入文档接口
     */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, Object> request) {
        String title = (String) request.get("title");
        String content = (String) request.get("content");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");

        String documentId = knowledgeBaseService.ingest(title, content, metadata);

        return Map.of(
                "status", "success",
                "documentId", documentId,
                "message", "文档已摄入并向量化");
    }

    /**
     * 向量检索接口
     */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String query) {
        List<Document> results = vectorStore.similaritySearch(query);

        return results.stream().map(doc -> {
            Map<String, Object> result = new HashMap<>();
            result.put("content", doc.getText());
            result.put("metadata", doc.getMetadata());
            return result;
        }).collect(Collectors.toList());
    }

    // --- 3. 从 IngestionController 迁移过来的方法 ---

    /**
     * 文件上传接口
     *
     * @param file 上传的文件
     * @return 包含任务ID的响应
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            String ingestionId = ingestionService.submitTask(file);
            Map<String, Object> response = Map.of(
                    "status", "success",
                    "ingestionId", ingestionId,
                    "message", "文件已提交后台处理"
            );
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("文件上传处理失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "文件保存失败"));
        }
    }

    /**
     * 查询任务状态接口
     *
     * @param ingestionId 任务ID
     */
    @GetMapping("/status/{ingestionId}")
    public ResponseEntity<Map<Object, Object>> getStatus(@PathVariable String ingestionId) {
        Map<Object, Object> status = ingestionService.getStatus(ingestionId);
        if (status.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * SSE 实时进度流接口
     */
    @GetMapping(value = "/status/stream/{ingestionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatus(@PathVariable String ingestionId) {
        return ingestionService.subscribeStatus(ingestionId);
    }
}
