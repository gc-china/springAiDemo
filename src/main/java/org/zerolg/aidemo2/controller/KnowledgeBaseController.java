package org.zerolg.aidemo2.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;
import org.zerolg.aidemo2.service.KnowledgeBaseService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai/knowledge")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorStore vectorStore;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService, VectorStore vectorStore) {
        this.knowledgeBaseService = knowledgeBaseService;
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
}
