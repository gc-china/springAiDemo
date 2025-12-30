// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.controller;

// 导入日志相关类，用于记录控制器操作过程
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// 导入Spring AI框架相关类，用于向量检索和文档处理
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
// 导入Spring框架的资源处理和HTTP相关类
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
// 导入Spring Web相关注解和类
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
// 导入项目自定义的数据访问层接口
import org.zerolg.aidemo2.mapper.DocumentMapper;
import org.zerolg.aidemo2.mapper.DocumentChunkMapper;
// 导入项目自定义的实体类
import org.zerolg.aidemo2.entity.DocumentChunk;
// 导入项目自定义的服务层接口
import org.zerolg.aidemo2.service.KnowledgeIngestionService;
import org.zerolg.aidemo2.service.KnowledgeBaseService;
// 导入MyBatis Plus查询条件构造器
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

// 导入Java标准库
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

/**
 * 知识库管理控制器
 * <p>
 * 这是知识库系统的核心REST API控制器，提供完整的知识库管理功能
 * <p>
 * 主要功能模块：
 * 1. 文档摄入管理
 * - 文件上传摄入：支持多种格式文档的上传和处理
 * - 纯文本摄入：直接摄入文本内容到知识库
 * - 异步处理：大文件异步摄入，实时进度反馈
 * <p>
 * 2. 文档检索服务
 * - 向量检索：基于语义相似度的智能检索
 * - 全文检索：基于关键词的精确匹配检索
 * - 混合检索：结合向量和关键词的综合检索
 * <p>
 * 3. 文档访问服务
 * - 文件下载：支持原始文件的安全下载
 * - 文件预览：支持在浏览器中直接预览文档
 * - 内容获取：获取文档的文本内容
 * - 引用信息：提供文档的详细引用信息
 * <p>
 * 4. 文档管理功能
 * - 文档列表：查看所有已摄入的文档
 * - 文档删除：单个或批量删除文档
 * - 数据清理：清理孤立的向量数据
 * - 状态查询：查询文档处理状态
 * <p>
 * 5. 实时监控
 * - SSE流：实时推送文档处理进度
 * - 状态跟踪：跟踪文档摄入的各个阶段
 * - 错误处理：完善的异常处理和错误反馈
 * <p>
 * 技术特点：
 * - RESTful设计：遵循REST API设计规范
 * - 异步处理：支持大文件的异步处理
 * - 实时反馈：通过SSE提供实时进度更新
 * - 安全下载：支持中文文件名和多种MIME类型
 * - 容错设计：完善的异常处理和降级策略
 * - 调试支持：提供调试接口帮助排查问题
 */
@RestController // Spring注解：标记这是一个REST控制器，自动序列化返回值为JSON
@RequestMapping("/api/ai/knowledge") // 统一的API基础路径，所有知识库相关接口都以此开头
public class KnowledgeBaseController {

    // 创建日志记录器，用于记录控制器的操作过程和异常信息
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseController.class);

    // 依赖注入的核心服务组件
    private final KnowledgeBaseService knowledgeBaseService; // 知识库核心业务服务
    private final KnowledgeIngestionService ingestionService; // 文档摄入服务，处理文件上传和异步处理
    private final VectorStore vectorStore; // 向量存储服务，用于语义检索
    private final DocumentMapper documentMapper; // 文档数据访问接口，用于查询文档信息
    private final DocumentChunkMapper documentChunkMapper; // 文档切片数据访问接口，用于查询切片内容

    /**
     * 构造函数 - 依赖注入
     *
     * Spring会自动注入所需的服务组件，确保控制器能够访问所有必要的业务逻辑
     *
     * @param knowledgeBaseService 知识库核心服务
     * @param ingestionService 文档摄入服务
     * @param vectorStore 向量存储服务
     * @param documentMapper 文档数据访问接口
     * @param documentChunkMapper 文档切片数据访问接口
     */
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
     * 纯文本摄入接口
     * 
     * 用于直接摄入纯文本内容到知识库，无需上传文件
     * 适用场景：
     * - 手动输入的文本内容
     * - 从其他系统复制的文本
     * - API调用传入的文本数据
     *
     * 处理流程：
     * 1. 接收文本内容和元数据
     * 2. 调用知识库服务进行文本切片
     * 3. 生成向量表示并存储
     * 4. 返回生成的文档ID
     *
     * @param request 请求体，包含title（标题）、content（内容）、metadata（元数据）
     * @return 包含状态、文档ID和消息的响应
     */
    @PostMapping("/ingest") // HTTP POST请求映射，路径为 /api/ai/knowledge/ingest
    public Map<String, Object> ingest(@RequestBody Map<String, Object> request) {
        // 从请求体中提取文档标题
        String title = (String) request.get("title");
        // 从请求体中提取文档内容
        String content = (String) request.get("content");
        // 从请求体中提取元数据（可选），使用@SuppressWarnings忽略类型转换警告
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");

        // 调用知识库服务进行纯文本摄入，返回生成的文档ID
        String documentId = knowledgeBaseService.ingest(title, content, metadata);

        // 构建成功响应，包含状态、文档ID和提示信息
        return Map.of(
                "status", "success", // 处理状态
                "documentId", documentId, // 生成的文档唯一标识
                "message", "文档已摄入并向量化"); // 用户友好的提示信息
    }

    /**
     * 向量检索接口
     * 
     * 基于语义相似度进行文档检索，使用向量数据库的相似性搜索功能
     *
     * 工作原理：
     * 1. 将用户查询转换为向量表示
     * 2. 在向量数据库中搜索最相似的文档片段
     * 3. 返回匹配的文档内容和元数据
     *
     * 适用场景：
     * - 语义搜索：理解用户意图，即使关键词不完全匹配
     * - 相关内容推荐：基于内容相似性推荐相关文档
     * - 智能问答：为AI问答系统提供相关上下文
     *
     * @param query 用户查询字符串
     * @return 匹配的文档列表，每个文档包含内容和元数据
     */
    @GetMapping("/search") // HTTP GET请求映射，路径为 /api/ai/knowledge/search
    public List<Map<String, Object>> search(@RequestParam String query) {
        // 调用向量存储服务进行相似性搜索
        List<Document> results = vectorStore.similaritySearch(query);

        // 将Spring AI的Document对象转换为前端友好的Map格式
        return results.stream().map(doc -> {
            // 为每个文档创建一个包含内容和元数据的Map
            Map<String, Object> result = new HashMap<>();
            result.put("content", doc.getText()); // 文档的文本内容
            result.put("metadata", doc.getMetadata()); // 文档的元数据信息
            return result;
        }).collect(Collectors.toList()); // 收集所有结果为List
    }

    /**
     * 文件下载接口
     * 
     * 提供安全的文件下载服务，支持根据文档ID下载原始文件
     *
     * 功能特点：
     * - 安全验证：检查文档是否存在和文件是否可访问
     * - 中文支持：正确处理中文文件名的编码
     * - MIME类型：自动设置正确的Content-Type
     * - 错误处理：详细的错误信息和状态码
     * - 临时文档处理：识别并拒绝临时引用文档的下载请求
     *
     * 支持的文件类型：
     * - 办公文档：PDF、Word、Excel、PowerPoint
     * - 文本文件：TXT、MD、HTML、JSON、XML
     * - 图片文件：JPG、PNG、GIF、BMP
     * - 其他格式：根据MIME类型自动识别
     *
     * @param documentId 文档的唯一标识符
     * @return 文件资源的HTTP响应，包含正确的Content-Disposition和Content-Type头
     */
    @GetMapping("/download/{documentId}") // HTTP GET请求映射，支持路径变量
    public ResponseEntity<Resource> downloadDocument(@PathVariable String documentId) {
        try {
            // 记录下载请求的开始
            logger.info("开始下载文档: documentId={}", documentId);

            // 处理临时ID的情况 - 这些是系统生成的临时引用，没有对应的物理文件
            if (documentId.startsWith("temp_") || documentId.startsWith("chunk_")) {
                logger.warn("尝试下载临时ID文档: documentId={}", documentId);
                // 返回400错误，并在响应头中提供详细的错误信息
                return ResponseEntity.badRequest()
                        .header("X-Error-Message", "该文档为临时引用，无法下载原始文件")
                        .header("X-Error-Code", "TEMP_DOCUMENT")
                        .build();
            }

            // 1. 查询文档信息 - 从数据库中获取文档的详细信息
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            if (document == null) {
                // 文档不存在，记录警告并返回404错误
                logger.warn("文档不存在: documentId={}", documentId);
                return ResponseEntity.notFound()
                        .header("X-Error-Message", "文档不存在或已被删除")
                        .header("X-Error-Code", "DOCUMENT_NOT_FOUND")
                        .build();
            }

            // 记录找到的文档信息
            logger.info("找到文档: title={}, filePath={}, source={}",
                    document.getTitle(), document.getFilePath(),
                    document.getMetadata().get("source"));

            // 2. 构建文件路径 - 获取文档对应的物理文件路径
            String filePath = document.getFilePath();
            if (filePath == null || filePath.isEmpty()) {
                // 手动摄入的文档没有对应的物理文件
                String source = (String) document.getMetadata().get("source");
                logger.warn("文档没有文件路径: documentId={}, source={}", documentId, source);
                if ("manual_ingest".equals(source)) {
                    // 纯文本摄入的文档，没有原始文件可下载
                    return ResponseEntity.badRequest()
                            .header("X-Error-Message", "纯文本文档无法下载")
                            .build();
                }
                // 其他情况的文件路径为空
                return ResponseEntity.notFound()
                        .header("X-Error-Message", "文件路径为空")
                        .build();
            }

            // 3. 检查文件是否存在 - 验证物理文件是否真实存在
            Path path = Paths.get(filePath);
            logger.info("检查文件路径: {}", path.toAbsolutePath());

            if (!Files.exists(path)) {
                // 文件不存在，可能已被删除或移动
                logger.warn("文件不存在: {}", path.toAbsolutePath());
                return ResponseEntity.notFound()
                        .header("X-Error-Message", "文件不存在: " + path.getFileName())
                        .build();
            }

            // 4. 创建文件资源 - 创建Spring的文件系统资源对象
            Resource resource = new FileSystemResource(path);

            // 5. 设置响应头 - 配置文件下载的HTTP响应头
            String filename = document.getTitle();
            if (filename == null || filename.isEmpty()) {
                // 如果文档标题为空，使用文件名
                filename = path.getFileName().toString();
            }

            // 处理中文文件名编码 - 使用RFC 5987标准的编码方式
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20"); // 将+号替换为%20，符合URL编码标准

            // 记录下载成功的信息
            logger.info("文件下载成功: filename={}, size={}", filename, resource.contentLength());

            // 构建并返回成功的响应
            return ResponseEntity.ok()
                    // 设置Content-Disposition头，告诉浏览器这是一个下载文件
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename)
                    // 设置Content-Type头，告诉浏览器文件的MIME类型
                    .header(HttpHeaders.CONTENT_TYPE,
                            document.getMimeType() != null ? document.getMimeType() : "application/octet-stream")
                    .body(resource); // 设置响应体为文件资源

        } catch (Exception e) {
            // 捕获所有异常，记录错误日志并返回500错误
            logger.error("文件下载失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", "下载失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 文件预览接口
     * 
     * 提供在浏览器中直接预览文档的功能，支持多种文件格式的在线查看
     *
     * 功能特点：
     * - 在线预览：文件在浏览器中直接显示，无需下载
     * - 格式支持：PDF、图片、文本文件等多种格式
     * - MIME类型智能识别：根据文件扩展名自动推断正确的MIME类型
     * - 字符编码处理：文本文件自动添加UTF-8编码声明
     * - 缓存控制：设置no-cache确保获取最新内容
     * - 安全检查：验证文档存在性和文件可访问性
     *
     * 支持的预览格式：
     * - PDF文档：application/pdf - 浏览器内置PDF查看器
     * - 图片文件：image/jpeg, image/png, image/gif - 直接显示
     * - 文本文件：text/plain, text/html, text/markdown - 文本查看
     * - 结构化数据：application/json, application/xml - 格式化显示
     *
     * 与下载接口的区别：
     * - Content-Disposition: inline（预览）vs attachment（下载）
     * - 浏览器行为：直接显示 vs 保存到本地
     * - 缓存策略：no-cache vs 默认缓存
     *
     * @param documentId 文档的唯一标识符
     * @return 文件资源的HTTP响应，配置为浏览器内预览
     */
    @GetMapping("/preview/{documentId}") // HTTP GET请求映射，支持路径变量
    public ResponseEntity<Resource> previewDocument(@PathVariable String documentId) {
        try {
            // 记录预览请求的开始
            logger.info("开始预览文档: documentId={}", documentId);

            // 处理临时ID的情况 - 临时引用文档没有对应的物理文件
            if (documentId.startsWith("temp_") || documentId.startsWith("chunk_")) {
                logger.warn("尝试预览临时ID文档: documentId={}", documentId);
                // 返回400错误，提供详细的错误信息
                return ResponseEntity.badRequest()
                        .header("X-Error-Message", "该文档为临时引用，无法预览原始文件")
                        .header("X-Error-Code", "TEMP_DOCUMENT")
                        .build();
            }

            // 1. 查询文档信息 - 从数据库获取文档的详细信息
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            if (document == null) {
                // 文档不存在，记录警告并返回404错误
                logger.warn("文档不存在: documentId={}", documentId);
                return ResponseEntity.notFound().build();
            }

            // 记录找到的文档信息
            logger.info("找到文档: title={}, filePath={}, source={}",
                    document.getTitle(), document.getFilePath(),
                    document.getMetadata().get("source"));

            // 2. 构建文件路径 - 获取文档对应的物理文件路径
            String filePath = document.getFilePath();
            if (filePath == null || filePath.isEmpty()) {
                // 手动摄入的文档没有对应的物理文件
                String source = (String) document.getMetadata().get("source");
                logger.warn("文档没有文件路径: documentId={}, source={}", documentId, source);
                if ("manual_ingest".equals(source)) {
                    // 纯文本摄入的文档，建议使用内容预览接口
                    return ResponseEntity.badRequest()
                            .header("X-Error-Message", "纯文本文档无法预览")
                            .build();
                }
                // 其他情况的文件路径为空
                return ResponseEntity.notFound()
                        .header("X-Error-Message", "文件路径为空")
                        .build();
            }

            // 3. 检查文件是否存在 - 验证物理文件是否真实存在
            Path path = Paths.get(filePath);
            logger.info("检查文件路径: {}", path.toAbsolutePath());

            if (!Files.exists(path)) {
                // 文件不存在，可能已被删除或移动
                logger.warn("文件不存在: {}", path.toAbsolutePath());
                return ResponseEntity.notFound()
                        .header("X-Error-Message", "文件不存在: " + path.getFileName())
                        .build();
            }

            // 4. 创建文件资源 - 创建Spring的文件系统资源对象
            Resource resource = new FileSystemResource(path);

            // 5. 设置响应头（inline表示在浏览器中预览，而非下载）
            String mimeType = document.getMimeType();
            if (mimeType == null) {
                // 根据文件扩展名推断MIME类型 - 确保浏览器正确处理文件
                String extension = getFileExtension(path.getFileName().toString()).toLowerCase();
                mimeType = switch (extension) {
                    case "pdf" -> "application/pdf"; // PDF文档
                    case "jpg", "jpeg" -> "image/jpeg"; // JPEG图片
                    case "png" -> "image/png"; // PNG图片
                    case "gif" -> "image/gif"; // GIF动图
                    case "txt" -> "text/plain; charset=utf-8"; // 纯文本
                    case "html", "htm" -> "text/html; charset=utf-8"; // HTML文档
                    case "md" -> "text/markdown; charset=utf-8"; // Markdown文档
                    case "json" -> "application/json; charset=utf-8"; // JSON数据
                    case "xml" -> "application/xml; charset=utf-8"; // XML文档
                    default -> "application/octet-stream"; // 二进制文件（默认）
                };
            } else if (mimeType.startsWith("text/") && !mimeType.contains("charset")) {
                // 如果是文本类型但没有指定字符集，添加UTF-8编码声明
                mimeType = mimeType + "; charset=utf-8";
            }

            // 记录预览成功的信息
            logger.info("文件预览成功: filename={}, mimeType={}, size={}",
                    path.getFileName(), mimeType, resource.contentLength());

            // 构建并返回成功的响应
            return ResponseEntity.ok()
                    // 设置Content-Disposition为inline，告诉浏览器直接显示文件
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    // 设置正确的Content-Type，确保浏览器正确解析文件
                    .header(HttpHeaders.CONTENT_TYPE, mimeType)
                    // 设置缓存控制，确保获取最新内容
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(resource); // 设置响应体为文件资源

        } catch (Exception e) {
            // 捕获所有异常，记录错误日志并返回500错误
            logger.error("文件预览失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", "预览失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 文本内容预览接口
     * 
     * 专门用于预览纯文本摄入文档的内容，将分散的文档切片重新组合为完整内容
     *
     * 功能特点：
     * - 内容重组：将文档的所有切片按顺序重新组合
     * - 完整信息：返回文档的完整元数据和统计信息
     * - 纯文本支持：专门处理手动摄入的纯文本文档
     * - 结构化响应：返回JSON格式的结构化数据
     *
     * 适用场景：
     * - 查看手动摄入的纯文本文档内容
     * - 预览没有物理文件的文档
     * - 获取文档的完整文本用于编辑或复制
     * - 调试文档切片和重组过程
     *
     * 返回数据包含：
     * - documentId: 文档唯一标识
     * - title: 文档标题
     * - content: 重组后的完整文本内容
     * - chunkCount: 切片数量
     * - totalTokens: 总token数量
     * - createdAt: 创建时间
     * - metadata: 文档元数据
     *
     * @param documentId 文档的唯一标识符
     * @return 包含完整文档内容和元数据的JSON响应
     */
    @GetMapping("/content/{documentId}") // HTTP GET请求映射，支持路径变量
    public ResponseEntity<Map<String, Object>> getDocumentContent(@PathVariable String documentId) {
        try {
            // 记录内容获取请求的开始
            logger.info("获取文档内容: documentId={}", documentId);

            // 1. 查询文档信息 - 验证文档是否存在
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            if (document == null) {
                // 文档不存在，记录警告并返回404错误
                logger.warn("文档不存在: documentId={}", documentId);
                return ResponseEntity.notFound().build();
            }

            // 2. 查询文档的所有切片内容 - 按切片索引顺序排列
            List<DocumentChunk> chunks = documentChunkMapper.selectList(
                    new LambdaQueryWrapper<DocumentChunk>()
                            .eq(DocumentChunk::getDocumentId, documentId) // 匹配文档ID
                            .orderByAsc(DocumentChunk::getChunkIndex) // 按切片索引升序排列
            );

            if (chunks.isEmpty()) {
                // 文档没有切片内容，可能是数据不完整
                logger.warn("文档没有切片内容: documentId={}", documentId);
                return ResponseEntity.notFound().build();
            }

            // 3. 重组完整内容 - 将所有切片按顺序拼接
            StringBuilder fullContent = new StringBuilder();
            for (DocumentChunk chunk : chunks) {
                // 将每个切片的内容添加到完整内容中
                fullContent.append(chunk.getContent()).append("\n\n"); // 切片间用双换行分隔
            }

            // 4. 构建响应 - 创建包含完整信息的响应对象
            Map<String, Object> response = new HashMap<>();
            response.put("documentId", documentId); // 文档ID
            response.put("title", document.getTitle()); // 文档标题
            response.put("content", fullContent.toString().trim()); // 完整内容（去除末尾空白）
            response.put("chunkCount", chunks.size()); // 切片数量
            response.put("totalTokens", document.getTotalTokens()); // 总token数
            response.put("createdAt", document.getCreatedAt()); // 创建时间
            response.put("metadata", document.getMetadata()); // 文档元数据

            // 记录成功获取内容的信息
            logger.info("文档内容获取成功: documentId={}, chunkCount={}", documentId, chunks.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // 捕获所有异常，记录错误日志并返回500错误
            logger.error("获取文档内容失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取内容失败: " + e.getMessage()));
        }
    }

    /**
     * 获取文档引用信息接口
     * 
     * 返回文档的详细引用信息，主要用于前端显示引用来源和提供访问链接
     *
     * 功能特点：
     * - 完整元数据：返回文档的所有基本信息和元数据
     * - 访问链接：自动生成下载和预览链接
     * - 文件状态检查：实时检查物理文件是否存在
     * - 状态标识：区分不同类型的文档（文件上传、纯文本、文件缺失等）
     *
     * 返回信息包含：
     * - 基本信息：ID、标题、文件路径、MIME类型等
     * - 统计信息：token数量、切片数量、创建时间等
     * - 访问链接：下载URL和预览URL
     * - 文件状态：文件是否存在、文件状态描述
     * - 元数据：完整的文档元数据信息
     *
     * 使用场景：
     * - AI回答中的引用链接生成
     * - 文档管理界面的详情显示
     * - 文档状态检查和诊断
     * - 前端组件的数据源
     *
     * @param documentId 文档的唯一标识符
     * @return 包含完整引用信息的JSON响应
     */
    @GetMapping("/citation/{documentId}") // HTTP GET请求映射，支持路径变量
    public ResponseEntity<Map<String, Object>> getDocumentCitation(@PathVariable String documentId) {
        try {
            // 1. 查询文档信息 - 从数据库获取文档的完整信息
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            if (document == null) {
                // 文档不存在，直接返回404错误
                return ResponseEntity.notFound().build();
            }

            // 2. 构建引用信息 - 创建包含所有必要信息的引用对象
            Map<String, Object> citation = new HashMap<>();
            citation.put("documentId", document.getId()); // 文档唯一标识
            citation.put("title", document.getTitle()); // 文档标题
            citation.put("filePath", document.getFilePath()); // 文件路径（可能为空）
            citation.put("mimeType", document.getMimeType()); // MIME类型
            citation.put("totalTokens", document.getTotalTokens()); // 总token数量
            citation.put("chunkCount", document.getChunkCount()); // 切片数量
            citation.put("createdAt", document.getCreatedAt()); // 创建时间
            citation.put("metadata", document.getMetadata()); // 完整元数据

            // 3. 添加访问链接 - 生成标准的下载和预览URL
            citation.put("downloadUrl", "/api/ai/knowledge/download/" + documentId);
            citation.put("previewUrl", "/api/ai/knowledge/preview/" + documentId);

            // 4. 检查文件是否存在 - 实时验证物理文件状态
            String filePath = document.getFilePath();
            boolean fileExists = false; // 文件是否存在的标志
            String fileStatus = "无文件"; // 文件状态的描述

            if (filePath != null && !filePath.isEmpty()) {
                // 有文件路径，检查物理文件是否存在
                fileExists = Files.exists(Paths.get(filePath));
                fileStatus = fileExists ? "文件正常" : "文件缺失";
            } else {
                // 没有文件路径，检查是否为手动摄入的纯文本文档
                String source = (String) document.getMetadata().get("source");
                if ("manual_ingest".equals(source)) {
                    fileStatus = "纯文本"; // 纯文本文档，正常情况
                }
                // 其他情况保持"无文件"状态
            }

            // 将文件状态信息添加到引用信息中
            citation.put("fileExists", fileExists); // 布尔值：文件是否存在
            citation.put("fileStatus", fileStatus); // 字符串：文件状态描述

            // 返回完整的引用信息
            return ResponseEntity.ok(citation);

        } catch (Exception e) {
            // 捕获所有异常，记录错误日志并返回500错误
            logger.error("获取文档引用信息失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 辅助方法：获取文件扩展名
     * 
     * 从文件名中提取文件扩展名，用于MIME类型推断
     *
     * @param filename 完整的文件名
     * @return 文件扩展名（不包含点号），如果没有扩展名则返回空字符串
     */
    private String getFileExtension(String filename) {
        // 查找最后一个点号的位置
        int lastDotIndex = filename.lastIndexOf('.');
        // 如果找到点号且不在文件名开头，返回扩展名；否则返回空字符串
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    /**
     * 文件上传接口
     * 
     * 提供文件上传和异步处理功能，支持多种文档格式的智能摄入
     *
     * 功能特点：
     * - 异步处理：文件上传后立即返回任务ID，后台异步处理
     * - 格式支持：支持PDF、Word、Excel、PowerPoint、文本文件等多种格式
     * - 进度跟踪：可通过任务ID查询处理进度和状态
     * - 错误处理：完善的异常处理和错误反馈机制
     * - 安全存储：文件安全存储到指定目录
     *
     * 处理流程：
     * 1. 接收上传的文件
     * 2. 验证文件格式和大小
     * 3. 保存文件到临时目录
     * 4. 提交异步处理任务
     * 5. 返回任务ID供客户端跟踪进度
     *
     * 后台处理包括：
     * - 文档解析（使用Apache Tika）
     * - 智能切片（使用SmartTextSplitter）
     * - 向量化存储（生成embedding并存储）
     * - 去重处理（文件级和切片级去重）
     *
     * @param file 上传的文件，通过multipart/form-data传输
     * @return 包含任务ID和状态信息的响应
     */
    @PostMapping("/upload") // HTTP POST请求映射，处理文件上传
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            // 提交文件处理任务到异步服务，获取任务ID
            String ingestionId = ingestionService.submitTask(file);
            
            // 构建成功响应，包含任务跟踪信息
            Map<String, Object> response = Map.of(
                    "status", "success", // 提交状态
                    "ingestionId", ingestionId, // 任务唯一标识，用于后续查询进度
                    "message", "文件已提交后台处理" // 用户友好的提示信息
            );
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            // 文件处理异常，记录错误日志并返回错误响应
            logger.error("文件上传处理失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "文件保存失败"
            ));
        }
    }

    /**
     * 获取文档列表接口
     * 
     * 返回知识库中所有已摄入文档的列表信息，支持文档管理和状态查看
     *
     * 功能特点：
     * - 完整列表：返回所有未删除的文档记录
     * - 状态检查：实时检查每个文档的文件状态
     * - 访问链接：为每个文档生成下载和预览链接
     * - 排序显示：按创建时间倒序排列，最新文档在前
     * - 逻辑删除过滤：自动过滤已标记删除的文档
     *
     * 返回信息包含：
     * - 基本信息：文档ID、标题、文件路径、MIME类型
     * - 统计信息：token数量、切片数量、创建时间
     * - 访问链接：下载URL和预览URL
     * - 文件状态：文件是否存在、状态描述
     * - 元数据：完整的文档元数据
     *
     * 文件状态说明：
     * - "文件正常"：有物理文件且文件存在
     * - "文件缺失"：有文件路径但物理文件不存在
     * - "纯文本"：手动摄入的纯文本，无物理文件
     * - "无文件"：其他没有文件路径的情况
     *
     * @return 文档列表的JSON响应，包含每个文档的详细信息
     */
    @GetMapping("/documents") // HTTP GET请求映射，获取文档列表
    public ResponseEntity<List<Map<String, Object>>> getDocuments() {
        try {
            // 1. 查询所有文档记录 - 按创建时间倒序排列
            List<org.zerolg.aidemo2.entity.Document> allDocuments = documentMapper.selectList(
                    new LambdaQueryWrapper<org.zerolg.aidemo2.entity.Document>()
                            .orderByDesc(org.zerolg.aidemo2.entity.Document::getCreatedAt) // 最新文档在前
            );

            // 2. 在应用层过滤未删除的记录 - 实现逻辑删除过滤
            List<org.zerolg.aidemo2.entity.Document> documents = allDocuments.stream()
                    .filter(doc -> doc.getIsDeleted() == null || !doc.getIsDeleted()) // 过滤已删除文档
                    .collect(Collectors.toList());

            // 3. 转换为前端友好的格式 - 为每个文档构建详细信息
            List<Map<String, Object>> result = documents.stream().map(doc -> {
                // 为每个文档创建信息映射
                Map<String, Object> docInfo = new HashMap<>();

                // 基本信息
                docInfo.put("documentId", doc.getId()); // 文档唯一标识
                docInfo.put("title", doc.getTitle()); // 文档标题
                docInfo.put("filePath", doc.getFilePath()); // 文件路径
                docInfo.put("mimeType", doc.getMimeType()); // MIME类型

                // 统计信息
                docInfo.put("totalTokens", doc.getTotalTokens()); // 总token数
                docInfo.put("chunkCount", doc.getChunkCount()); // 切片数量
                docInfo.put("createdAt", doc.getCreatedAt()); // 创建时间
                docInfo.put("metadata", doc.getMetadata()); // 元数据

                // 添加访问链接 - 生成标准的下载和预览URL
                docInfo.put("downloadUrl", "/api/ai/knowledge/download/" + doc.getId());
                docInfo.put("previewUrl", "/api/ai/knowledge/preview/" + doc.getId());

                // 检查文件是否存在 - 实时验证文件状态
                String filePath = doc.getFilePath();
                boolean fileExists = false; // 文件存在标志
                String fileStatus = "无文件"; // 默认状态

                if (filePath != null && !filePath.isEmpty()) {
                    // 有文件路径，检查物理文件是否存在
                    fileExists = Files.exists(Paths.get(filePath));
                    fileStatus = fileExists ? "文件正常" : "文件缺失";
                } else {
                    // 没有文件路径，检查文档来源
                    String source = (String) doc.getMetadata().get("source");
                    if ("manual_ingest".equals(source)) {
                        fileStatus = "纯文本"; // 手动摄入的纯文本文档
                    }
                    // 其他情况保持"无文件"状态
                }

                // 添加文件状态信息
                docInfo.put("fileExists", fileExists); // 布尔值：文件是否存在
                docInfo.put("fileStatus", fileStatus); // 字符串：状态描述

                return docInfo;
            }).collect(Collectors.toList());

            // 返回文档列表
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            // 捕获所有异常，记录错误日志并返回500错误
            logger.error("获取文档列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除文档接口
     * 
     * 提供单个文档的完整删除功能，包括所有相关数据的清理
     *
     * 删除范围：
     * - 文档记录：从document表中删除文档基本信息
     * - 切片记录：从document_chunk表中删除所有相关切片
     * - 向量数据：从vector_store表中删除所有相关向量
     * - 物理文件：删除磁盘上的原始文件（可选）
     *
     * 安全特性：
     * - 事务保护：使用数据库事务确保删除操作的原子性
     * - 详细日志：记录删除过程的每个步骤
     * - 错误处理：删除失败时提供详细的错误信息
     * - 状态反馈：返回详细的删除结果统计
     *
     * 删除流程：
     * 1. 验证文档是否存在
     * 2. 查询并删除相关的向量数据
     * 3. 删除文档切片记录
     * 4. 删除文档主记录
     * 5. 删除物理文件（如果存在）
     * 6. 返回删除结果统计
     *
     * @param documentId 要删除的文档ID
     * @return 删除结果，包含删除的记录数量和操作状态
     */
    @DeleteMapping("/document/{documentId}") // HTTP DELETE请求映射，支持路径变量
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String documentId) {
        try {
            // 记录删除请求的开始
            logger.info("收到删除文档请求: documentId={}", documentId);

            // 使用KnowledgeBaseService的完整删除方法 - 确保所有相关数据都被正确清理
            Map<String, Object> result = knowledgeBaseService.deleteDocument(documentId);

            // 根据删除结果返回相应的HTTP状态码
            if ("success".equals(result.get("status"))) {
                // 删除成功，记录成功日志并返回200 OK
                logger.info("文档删除成功: {}", result);
                return ResponseEntity.ok(result);
            } else {
                // 删除失败，记录警告日志并返回400 Bad Request
                logger.warn("文档删除失败: {}", result);
                return ResponseEntity.badRequest().body(result);
            }

        } catch (Exception e) {
            // 捕获所有异常，记录错误日志并返回500错误
            logger.error("删除文档异常: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "删除文档异常: " + e.getMessage(),
                            "documentId", documentId
                    ));
        }
    }

    /**
     * 批量删除文档接口
     * 
     * 提供多个文档的批量删除功能，提高删除效率
     *
     * 功能特点：
     * - 批量处理：一次请求删除多个文档
     * - 独立处理：每个文档独立删除，单个失败不影响其他文档
     * - 统计反馈：返回成功和失败的数量统计
     * - 详细结果：返回每个文档的删除结果
     * - 参数验证：验证文档ID列表的有效性
     *
     * 处理策略：
     * - 逐个删除：依次处理每个文档ID
     * - 容错处理：单个文档删除失败不中断整个批量操作
     * - 结果汇总：统计成功和失败的数量
     * - 详细报告：返回每个文档的具体删除结果
     *
     * @param request 请求体，包含documentIds数组
     * @return 批量删除结果，包含总体统计和详细结果
     */
    @DeleteMapping("/documents") // HTTP DELETE请求映射，处理批量删除
    public ResponseEntity<Map<String, Object>> deleteDocuments(@RequestBody Map<String, Object> request) {
        try {
            // 从请求体中提取文档ID列表
            @SuppressWarnings("unchecked")
            List<String> documentIds = (List<String>) request.get("documentIds");

            // 验证参数有效性
            if (documentIds == null || documentIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "文档ID列表不能为空"
                ));
            }

            // 记录批量删除请求的开始
            logger.info("收到批量删除文档请求: count={}", documentIds.size());

            // 调用知识库服务执行批量删除
            Map<String, Object> result = knowledgeBaseService.deleteDocuments(documentIds);

            // 返回批量删除结果
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            // 捕获所有异常，记录错误日志并返回500错误
            logger.error("批量删除文档异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "批量删除异常: " + e.getMessage()));
        }
    }

    /**
     * 清理孤立向量数据接口
     * 
     * 清理没有对应文档记录的孤立向量数据，维护数据库的一致性
     *
     * 功能说明：
     * - 数据一致性：确保向量数据与文档记录的一致性
     * - 存储优化：清理无用的向量数据，释放存储空间
     * - 性能提升：减少无效数据，提升检索性能
     * - 维护工具：提供数据库维护和清理功能
     *
     * 清理策略：
     * - 孤立检测：找出没有对应文档记录的向量数据
     * - 安全清理：使用事务确保清理操作的安全性
     * - 统计报告：返回清理的数据量统计
     * - 日志记录：详细记录清理过程和结果
     *
     * 使用场景：
     * - 定期维护：定期清理系统中的孤立数据
     * - 故障恢复：修复数据不一致问题
     * - 存储优化：释放不必要的存储空间
     * - 性能调优：提升向量检索的性能
     *
     * @return 清理结果，包含清理的数据量和操作状态
     */
    @PostMapping("/cleanup/orphaned-vectors") // HTTP POST请求映射，执行清理操作
    public ResponseEntity<Map<String, Object>> cleanupOrphanedVectors() {
        try {
            // 记录清理请求的开始
            logger.info("收到清理孤立向量数据请求");

            // 调用知识库服务执行孤立向量数据清理
            Map<String, Object> result = knowledgeBaseService.cleanupOrphanedVectors();

            // 返回清理结果
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            // 捕获所有异常，记录错误日志并返回500错误
            logger.error("清理孤立向量数据异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "清理异常: " + e.getMessage()));
        }
    }

    /**
     * 调试引用链接问题的专用接口
     * 
     * 提供引用链接生成和验证的调试功能，帮助排查引用链接相关问题
     *
     * 调试功能：
     * - URL生成验证：验证下载和预览URL的生成逻辑
     * - 文档存在性检查：验证文档是否在数据库中存在
     * - 链接格式验证：检查URL格式是否正确
     * - 引用数据结构：展示完整的引用数据结构
     * - 测试建议：提供具体的测试命令和建议
     *
     * 返回信息：
     * - 文档存在状态：文档是否在数据库中存在
     * - 生成的URL：下载和预览链接
     * - 引用数据：完整的引用数据结构
     * - URL验证：链接格式和内容的验证结果
     * - 测试建议：具体的测试命令和步骤
     *
     * 使用场景：
     * - 问题排查：当引用链接不工作时进行调试
     * - 开发测试：验证引用链接生成逻辑
     * - 集成测试：测试前后端引用链接的集成
     * - 故障诊断：快速定位引用相关问题
     *
     * @param documentId 要调试的文档ID
     * @return 详细的调试信息和测试建议
     */
    @GetMapping("/debug/citation-links/{documentId}") // HTTP GET请求映射，调试专用接口
    public ResponseEntity<Map<String, Object>> debugCitationLinks(@PathVariable String documentId) {
        try {
            // 记录调试请求的开始
            logger.info("调试引用链接: documentId={}", documentId);

            // 创建调试结果容器
            Map<String, Object> result = new HashMap<>();

            // 1. 检查文档是否存在 - 验证文档在数据库中的存在性
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            boolean documentExists = document != null;

            // 2. 生成URL（与AiService完全相同的逻辑） - 确保调试结果与实际使用一致
            String downloadUrl = "/api/ai/knowledge/download/" + documentId;
            String previewUrl = "/api/ai/knowledge/preview/" + documentId;

            // 3. 构建引用数据（与AiService完全相同的结构） - 模拟实际的引用数据
            Map<String, Object> citationData = new HashMap<>();
            citationData.put("documentId", documentId);
            citationData.put("downloadUrl", downloadUrl);
            citationData.put("previewUrl", previewUrl);

            if (documentExists) {
                // 文档存在，添加真实的文档信息
                citationData.put("filename", document.getTitle());
                citationData.put("fileStatus", document.getFilePath() != null ? "文件正常" : "纯文本");
                citationData.put("mimeType", document.getMimeType());
            } else {
                // 文档不存在，添加占位信息
                citationData.put("filename", "文档不存在");
                citationData.put("fileStatus", "不存在");
                citationData.put("mimeType", null);
            }

            // 4. 构建调试信息 - 提供详细的调试数据
            result.put("documentId", documentId); // 被调试的文档ID
            result.put("documentExists", documentExists); // 文档是否存在
            result.put("generatedUrls", Map.of( // 生成的URL信息
                    "downloadUrl", downloadUrl,
                    "previewUrl", previewUrl
            ));
            result.put("citationData", citationData); // 完整的引用数据
            result.put("urlValidation", Map.of( // URL验证信息
                    "downloadUrlLength", downloadUrl.length(), // URL长度
                    "previewUrlLength", previewUrl.length(),
                    "downloadUrlFormat", downloadUrl.startsWith("/api/ai/knowledge/download/"), // 格式检查
                    "previewUrlFormat", previewUrl.startsWith("/api/ai/knowledge/preview/"),
                    "documentIdInUrls", downloadUrl.contains(documentId) && previewUrl.contains(documentId) // ID包含检查
            ));

            // 5. 测试建议 - 提供具体的测试命令和步骤
            result.put("testSuggestions", Map.of(
                    "directDownloadTest", "curl -I 'http://localhost:8080" + downloadUrl + "'", // 直接下载测试
                    "directPreviewTest", "curl -I 'http://localhost:8080" + previewUrl + "'", // 直接预览测试
                    "frontendTest", "检查前端是否正确处理citations事件中的downloadUrl和previewUrl字段" // 前端集成测试
            ));

            // 记录调试完成信息
            logger.info("引用链接调试完成: documentExists={}, urls=[{}, {}]",
                    documentExists, downloadUrl, previewUrl);

            // 返回调试结果
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            // 捕获所有异常，记录错误日志并返回500错误
            logger.error("调试引用链接失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "调试失败", "message", e.getMessage()));
        }
    }

    /**
     * 查询任务状态接口
     * 
     * 提供文档摄入任务的状态查询功能，支持实时跟踪处理进度
     *
     * 功能特点：
     * - 状态跟踪：实时查询任务的处理状态
     * - 进度反馈：返回任务的完成进度百分比
     * - 错误信息：提供详细的错误信息和失败原因
     * - 结果获取：任务完成后可获取处理结果
     *
     * 状态类型：
     * - PENDING：任务等待处理
     * - PROCESSING：任务正在处理中
     * - COMPLETED：任务处理完成
     * - FAILED：任务处理失败
     *
     * 返回信息：
     * - 任务状态：当前的处理状态
     * - 进度百分比：任务完成的百分比
     * - 状态消息：详细的状态描述
     * - 错误信息：失败时的错误详情
     * - 结果数据：成功时的处理结果
     *
     * @param ingestionId 摄入任务的唯一标识符
     * @return 任务状态信息，如果任务不存在则返回404
     */
    @GetMapping("/status/{ingestionId}") // HTTP GET请求映射，查询任务状态
    public ResponseEntity<Map<Object, Object>> getStatus(@PathVariable String ingestionId) {
        // 调用摄入服务获取任务状态
        Map<Object, Object> status = ingestionService.getStatus(ingestionId);
        
        // 检查任务是否存在
        if (status.isEmpty()) {
            // 任务不存在，返回404 Not Found
            return ResponseEntity.notFound().build();
        }
        
        // 返回任务状态信息
        return ResponseEntity.ok(status);
    }

    /**
     * SSE实时进度流接口
     *
     * 提供Server-Sent Events (SSE)实时推送任务处理进度的功能
     *
     * 功能特点：
     * - 实时推送：通过SSE协议实时推送进度更新
     * - 长连接：保持连接直到任务完成或失败
     * - 自动断开：任务结束后自动关闭连接
     * - 错误处理：连接异常时自动重试或断开
     *
     * SSE事件类型：
     * - progress：进度更新事件
     * - status：状态变更事件
     * - complete：任务完成事件
     * - error：错误发生事件
     *
     * 使用方式：
     * ```javascript
     * const eventSource = new EventSource('/api/ai/knowledge/status/stream/taskId');
     * eventSource.onmessage = function(event) {
     *     const data = JSON.parse(event.data);
     *     // 处理进度更新
     * };
     * ```
     *
     * @param ingestionId 摄入任务的唯一标识符
     * @return SSE发射器，用于推送实时进度信息
     */
    @GetMapping(value = "/status/stream/{ingestionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // SSE内容类型
    public SseEmitter streamStatus(@PathVariable String ingestionId) {
        // 调用摄入服务创建SSE发射器，订阅任务状态更新
        return ingestionService.subscribeStatus(ingestionId);
    }
}
