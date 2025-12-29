package org.zerolg.aidemo2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zerolg.aidemo2.service.RagService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 引用功能测试控制器
 */
@RestController
@RequestMapping("/api/test")
public class CitationTestController {

    private static final Logger logger = LoggerFactory.getLogger(CitationTestController.class);

    private final RagService ragService;

    public CitationTestController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 测试引用数据格式
     */
    @GetMapping("/citations")
    public ResponseEntity<Map<String, Object>> testCitations(@RequestParam String query) {
        try {
            logger.info("测试引用数据格式: query={}", query);

            // 执行检索
            List<Document> documents = ragService.retrieveAndRerank(query).block();

            if (documents == null || documents.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", "没有找到相关文档",
                        "query", query,
                        "documents", List.of()
                ));
            }

            // 构建引用信息（模拟 AiService 的逻辑）
            List<Map<String, Object>> citationsData = documents.stream()
                    .map(doc -> {
                        Map<String, Object> metadata = doc.getMetadata();
                        Map<String, Object> citation = new HashMap<>();

                        // 基本信息
                        String documentId = (String) metadata.get("source_document_id");
                        citation.put("documentId", documentId);
                        citation.put("filename", metadata.getOrDefault("source_filename", "未知文件"));
                        citation.put("location", "第" + ((Integer) metadata.getOrDefault("source_chunk_index", 0) + 1) + "段");
                        citation.put("citationNumber", metadata.get("citation_number"));

                        // 访问链接
                        if (documentId != null && !documentId.isEmpty()) {
                            citation.put("downloadUrl", "/api/ai/knowledge/download/" + documentId);
                            citation.put("previewUrl", "/api/ai/knowledge/preview/" + documentId);
                        } else {
                            citation.put("downloadUrl", null);
                            citation.put("previewUrl", null);
                        }

                        // 文件状态和类型信息
                        citation.put("fileStatus", metadata.getOrDefault("file_status", "未知"));
                        citation.put("fileExists", !"纯文本".equals(metadata.get("file_status")));
                        citation.put("mimeType", metadata.get("source_mime_type"));

                        // 调试信息
                        citation.put("debug_metadata", metadata);

                        return citation;
                    })
                    .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("documentCount", documents.size());
            result.put("citations", citationsData);
            result.put("message", "引用数据格式测试");

            logger.info("引用数据测试结果: {}", result);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("测试引用数据失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "测试失败",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 比较文档库和AI回答的引用格式
     */
    @GetMapping("/compare-citations/{documentId}")
    public ResponseEntity<Map<String, Object>> compareCitations(@PathVariable String documentId) {
        try {
            Map<String, Object> result = new HashMap<>();

            // 文档库格式（从 KnowledgeBaseController 获取）
            Map<String, Object> libraryFormat = new HashMap<>();
            libraryFormat.put("downloadUrl", "/api/ai/knowledge/download/" + documentId);
            libraryFormat.put("previewUrl", "/api/ai/knowledge/preview/" + documentId);
            libraryFormat.put("source", "文档库");

            // AI回答格式（从 AiService 获取）
            Map<String, Object> aiFormat = new HashMap<>();
            aiFormat.put("downloadUrl", "/api/ai/knowledge/download/" + documentId);
            aiFormat.put("previewUrl", "/api/ai/knowledge/preview/" + documentId);
            aiFormat.put("source", "AI回答");

            result.put("documentId", documentId);
            result.put("libraryFormat", libraryFormat);
            result.put("aiFormat", aiFormat);
            result.put("urlsMatch", libraryFormat.get("downloadUrl").equals(aiFormat.get("downloadUrl")));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("比较引用格式失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "比较失败",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 测试引用URL的有效性
     */
    @GetMapping("/validate-citation-urls/{documentId}")
    public ResponseEntity<Map<String, Object>> validateCitationUrls(@PathVariable String documentId) {
        try {
            Map<String, Object> result = new HashMap<>();

            // 生成URL（模拟AiService的逻辑）
            String downloadUrl = "/api/ai/knowledge/download/" + documentId;
            String previewUrl = "/api/ai/knowledge/preview/" + documentId;

            result.put("documentId", documentId);
            result.put("generatedUrls", Map.of(
                    "downloadUrl", downloadUrl,
                    "previewUrl", previewUrl
            ));

            // 验证URL格式
            boolean downloadUrlValid = downloadUrl.matches("/api/ai/knowledge/download/[a-fA-F0-9-]+");
            boolean previewUrlValid = previewUrl.matches("/api/ai/knowledge/preview/[a-fA-F0-9-]+");

            result.put("validation", Map.of(
                    "downloadUrlValid", downloadUrlValid,
                    "previewUrlValid", previewUrlValid,
                    "bothValid", downloadUrlValid && previewUrlValid
            ));

            // 提供测试链接
            result.put("testInstructions", Map.of(
                    "downloadTest", "访问: " + downloadUrl,
                    "previewTest", "访问: " + previewUrl,
                    "note", "这些链接应该能正常工作（如果文档存在）"
            ));

            logger.info("引用URL验证结果: documentId={}, downloadValid={}, previewValid={}",
                    documentId, downloadUrlValid, previewUrlValid);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("验证引用URL失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "验证失败",
                    "message", e.getMessage()
            ));
        }
    }
}