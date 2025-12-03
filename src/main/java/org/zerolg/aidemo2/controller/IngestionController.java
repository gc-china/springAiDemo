package org.zerolg.aidemo2.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.zerolg.aidemo2.service.KnowledgeIngestionService;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/ai/knowledge")
public class IngestionController {

    private final KnowledgeIngestionService ingestionService;

    public IngestionController(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * 上传文档 (异步处理)
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            String ingestionId = ingestionService.submitTask(file);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "ingestionId", ingestionId,
                "message", "文件已接收，开始异步处理"
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "文件上传失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 查询处理状态
     */
    @GetMapping("/status/{ingestionId}")
    public ResponseEntity<Map<Object, Object>> getStatus(@PathVariable String ingestionId) {
        Map<Object, Object> status = ingestionService.getStatus(ingestionId);
        if (status == null || status.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
