package org.zerolg.aidemo2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.zerolg.aidemo2.mapper.DocumentMapper;
import org.zerolg.aidemo2.mapper.DocumentChunkMapper;
import org.zerolg.aidemo2.entity.DocumentChunk;
import org.zerolg.aidemo2.service.KnowledgeIngestionService;
import org.zerolg.aidemo2.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final DocumentMapper documentMapper; // 新增：用于查询文档信息
    private final DocumentChunkMapper documentChunkMapper; // 新增：用于查询文档切片

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeIngestionService ingestionService,
                                   VectorStore vectorStore,
                                   DocumentMapper documentMapper,
                                   DocumentChunkMapper documentChunkMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.ingestionService = ingestionService;
        this.vectorStore = vectorStore;
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
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

    /**
     * 文件下载接口
     * 支持根据 documentId 下载原始文件
     */
    @GetMapping("/download/{documentId}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String documentId) {
        try {
            logger.info("开始下载文档: documentId={}", documentId);

            // 1. 查询文档信息
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            if (document == null) {
                logger.warn("文档不存在: documentId={}", documentId);
                return ResponseEntity.notFound().build();
            }

            logger.info("找到文档: title={}, filePath={}, source={}",
                    document.getTitle(), document.getFilePath(),
                    document.getMetadata().get("source"));

            // 2. 构建文件路径
            String filePath = document.getFilePath();
            if (filePath == null || filePath.isEmpty()) {
                // 手动摄入的文档没有对应的物理文件
                String source = (String) document.getMetadata().get("source");
                logger.warn("文档没有文件路径: documentId={}, source={}", documentId, source);
                if ("manual_ingest".equals(source)) {
                    return ResponseEntity.badRequest()
                            .header("X-Error-Message", "纯文本文档无法下载")
                            .build();
                }
                return ResponseEntity.notFound()
                        .header("X-Error-Message", "文件路径为空")
                        .build();
            }

            // 3. 检查文件是否存在
            Path path = Paths.get(filePath);
            logger.info("检查文件路径: {}", path.toAbsolutePath());

            if (!Files.exists(path)) {
                logger.warn("文件不存在: {}", path.toAbsolutePath());
                return ResponseEntity.notFound()
                        .header("X-Error-Message", "文件不存在: " + path.getFileName())
                        .build();
            }

            // 4. 创建文件资源
            Resource resource = new FileSystemResource(path);

            // 5. 设置响应头
            String filename = document.getTitle();
            if (filename == null || filename.isEmpty()) {
                filename = path.getFileName().toString();
            }

            // 处理中文文件名编码
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            logger.info("文件下载成功: filename={}, size={}", filename, resource.contentLength());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    .header(HttpHeaders.CONTENT_TYPE,
                            document.getMimeType() != null ? document.getMimeType() : "application/octet-stream")
                    .body(resource);

        } catch (Exception e) {
            logger.error("文件下载失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", "下载失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 文件预览接口
     * 支持在浏览器中直接预览文件（如 PDF、图片等）
     */
    @GetMapping("/preview/{documentId}")
    public ResponseEntity<Resource> previewDocument(@PathVariable String documentId) {
        try {
            logger.info("开始预览文档: documentId={}", documentId);

            // 1. 查询文档信息
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            if (document == null) {
                logger.warn("文档不存在: documentId={}", documentId);
                return ResponseEntity.notFound().build();
            }

            logger.info("找到文档: title={}, filePath={}, source={}",
                    document.getTitle(), document.getFilePath(),
                    document.getMetadata().get("source"));

            // 2. 构建文件路径
            String filePath = document.getFilePath();
            if (filePath == null || filePath.isEmpty()) {
                // 手动摄入的文档没有对应的物理文件
                String source = (String) document.getMetadata().get("source");
                logger.warn("文档没有文件路径: documentId={}, source={}", documentId, source);
                if ("manual_ingest".equals(source)) {
                    return ResponseEntity.badRequest()
                            .header("X-Error-Message", "纯文本文档无法预览")
                            .build();
                }
                return ResponseEntity.notFound()
                        .header("X-Error-Message", "文件路径为空")
                        .build();
            }

            // 3. 检查文件是否存在
            Path path = Paths.get(filePath);
            logger.info("检查文件路径: {}", path.toAbsolutePath());

            if (!Files.exists(path)) {
                logger.warn("文件不存在: {}", path.toAbsolutePath());
                return ResponseEntity.notFound()
                        .header("X-Error-Message", "文件不存在: " + path.getFileName())
                        .build();
            }

            // 4. 创建文件资源
            Resource resource = new FileSystemResource(path);

            // 5. 设置响应头（inline 表示在浏览器中预览）
            String mimeType = document.getMimeType();
            if (mimeType == null) {
                // 根据文件扩展名推断 MIME 类型
                String extension = getFileExtension(path.getFileName().toString()).toLowerCase();
                mimeType = switch (extension) {
                    case "pdf" -> "application/pdf";
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "gif" -> "image/gif";
                    case "txt" -> "text/plain; charset=utf-8";
                    case "html", "htm" -> "text/html; charset=utf-8";
                    case "md" -> "text/markdown; charset=utf-8";
                    case "json" -> "application/json; charset=utf-8";
                    case "xml" -> "application/xml; charset=utf-8";
                    default -> "application/octet-stream";
                };
            } else if (mimeType.startsWith("text/") && !mimeType.contains("charset")) {
                // 如果是文本类型但没有指定字符集，添加 UTF-8
                mimeType = mimeType + "; charset=utf-8";
            }

            logger.info("文件预览成功: filename={}, mimeType={}, size={}",
                    path.getFileName(), mimeType, resource.contentLength());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header(HttpHeaders.CONTENT_TYPE, mimeType)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(resource);

        } catch (Exception e) {
            logger.error("文件预览失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", "预览失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 文本内容预览接口
     * 用于预览纯文本摄入的文档内容
     */
    @GetMapping("/content/{documentId}")
    public ResponseEntity<Map<String, Object>> getDocumentContent(@PathVariable String documentId) {
        try {
            logger.info("获取文档内容: documentId={}", documentId);

            // 1. 查询文档信息
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            if (document == null) {
                logger.warn("文档不存在: documentId={}", documentId);
                return ResponseEntity.notFound().build();
            }

            // 2. 查询文档的所有切片内容
            List<DocumentChunk> chunks = documentChunkMapper.selectList(
                    new LambdaQueryWrapper<DocumentChunk>()
                            .eq(DocumentChunk::getDocumentId, documentId)
                            .orderByAsc(DocumentChunk::getChunkIndex)
            );

            if (chunks.isEmpty()) {
                logger.warn("文档没有切片内容: documentId={}", documentId);
                return ResponseEntity.notFound().build();
            }

            // 3. 重组完整内容
            StringBuilder fullContent = new StringBuilder();
            for (DocumentChunk chunk : chunks) {
                fullContent.append(chunk.getContent()).append("\n\n");
            }

            // 4. 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("documentId", documentId);
            response.put("title", document.getTitle());
            response.put("content", fullContent.toString().trim());
            response.put("chunkCount", chunks.size());
            response.put("totalTokens", document.getTotalTokens());
            response.put("createdAt", document.getCreatedAt());
            response.put("metadata", document.getMetadata());

            logger.info("文档内容获取成功: documentId={}, chunkCount={}", documentId, chunks.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取文档内容失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取内容失败: " + e.getMessage()));
        }
    }

    /**
     * 获取文档引用信息接口
     * 返回文档的详细信息，用于前端显示引用来源
     */
    @GetMapping("/citation/{documentId}")
    public ResponseEntity<Map<String, Object>> getDocumentCitation(@PathVariable String documentId) {
        try {
            // 1. 查询文档信息
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            if (document == null) {
                return ResponseEntity.notFound().build();
            }

            // 2. 构建引用信息
            Map<String, Object> citation = new HashMap<>();
            citation.put("documentId", document.getId());
            citation.put("title", document.getTitle());
            citation.put("filePath", document.getFilePath());
            citation.put("mimeType", document.getMimeType());
            citation.put("totalTokens", document.getTotalTokens());
            citation.put("chunkCount", document.getChunkCount());
            citation.put("createdAt", document.getCreatedAt());
            citation.put("metadata", document.getMetadata());

            // 3. 添加访问链接
            citation.put("downloadUrl", "/api/ai/knowledge/download/" + documentId);
            citation.put("previewUrl", "/api/ai/knowledge/preview/" + documentId);

            // 4. 检查文件是否存在
            String filePath = document.getFilePath();
            boolean fileExists = false;
            String fileStatus = "无文件";

            if (filePath != null && !filePath.isEmpty()) {
                fileExists = Files.exists(Paths.get(filePath));
                fileStatus = fileExists ? "文件正常" : "文件缺失";
            } else {
                // 手动摄入的文档，没有对应的物理文件
                String source = (String) document.getMetadata().get("source");
                if ("manual_ingest".equals(source)) {
                    fileStatus = "纯文本";
                }
            }

            citation.put("fileExists", fileExists);
            citation.put("fileStatus", fileStatus);

            return ResponseEntity.ok(citation);

        } catch (Exception e) {
            logger.error("获取文档引用信息失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 辅助方法：获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

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
     * 获取文档列表接口
     * 返回所有已摄入的文档信息
     */
    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> getDocuments() {
        try {
            // 方案1：直接查询所有记录，不使用逻辑删除过滤
            // 如果需要过滤已删除记录，在应用层处理
            List<org.zerolg.aidemo2.entity.Document> allDocuments = documentMapper.selectList(
                    new LambdaQueryWrapper<org.zerolg.aidemo2.entity.Document>()
                            .orderByDesc(org.zerolg.aidemo2.entity.Document::getCreatedAt)
            );

            // 在应用层过滤未删除的记录
            List<org.zerolg.aidemo2.entity.Document> documents = allDocuments.stream()
                    .filter(doc -> doc.getIsDeleted() == null || !doc.getIsDeleted())
                    .collect(Collectors.toList());

            List<Map<String, Object>> result = documents.stream().map(doc -> {
                Map<String, Object> docInfo = new HashMap<>();
                docInfo.put("documentId", doc.getId());
                docInfo.put("title", doc.getTitle());
                docInfo.put("filePath", doc.getFilePath());
                docInfo.put("mimeType", doc.getMimeType());
                docInfo.put("totalTokens", doc.getTotalTokens());
                docInfo.put("chunkCount", doc.getChunkCount());
                docInfo.put("createdAt", doc.getCreatedAt());
                docInfo.put("metadata", doc.getMetadata());

                // 添加访问链接
                docInfo.put("downloadUrl", "/api/ai/knowledge/download/" + doc.getId());
                docInfo.put("previewUrl", "/api/ai/knowledge/preview/" + doc.getId());

                // 检查文件是否存在
                String filePath = doc.getFilePath();
                boolean fileExists = false;
                String fileStatus = "无文件"; // 默认状态

                if (filePath != null && !filePath.isEmpty()) {
                    fileExists = Files.exists(Paths.get(filePath));
                    fileStatus = fileExists ? "文件正常" : "文件缺失";
                } else {
                    // 手动摄入的文档，没有对应的物理文件
                    String source = (String) doc.getMetadata().get("source");
                    if ("manual_ingest".equals(source)) {
                        fileStatus = "纯文本";
                    }
                }

                docInfo.put("fileExists", fileExists);
                docInfo.put("fileStatus", fileStatus);

                return docInfo;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("获取文档列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除文档接口
     * 删除文档及其相关的切片和向量数据
     */
    @DeleteMapping("/document/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String documentId) {
        try {
            // 1. 查询文档信息
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            if (document == null) {
                return ResponseEntity.notFound().build();
            }

            // 2. 删除文档记录
            documentMapper.deleteById(documentId);

            // 3. 删除相关切片（通过外键约束自动删除）
            // documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
            //     .eq(DocumentChunk::getDocumentId, documentId));

            // 4. 删除向量数据（需要根据 document_id 删除）
            // 注意：这里需要添加相应的 Mapper 方法来删除向量数据

            // 5. 删除物理文件（可选）
            String filePath = document.getFilePath();
            if (filePath != null && Files.exists(Paths.get(filePath))) {
                try {
                    Files.delete(Paths.get(filePath));
                    logger.info("已删除物理文件: {}", filePath);
                } catch (Exception e) {
                    logger.warn("删除物理文件失败: {}", filePath, e);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "文档删除成功");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("删除文档失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "删除文档失败"));
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
