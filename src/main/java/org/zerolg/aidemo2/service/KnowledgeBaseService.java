// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.service;

// 导入MyBatis Plus查询条件构造器，用于构建数据库查询条件
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// 导入日志相关类，用于记录系统运行信息
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// 导入Spring AI框架的文档类，用于向量存储
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
// 导入Spring框架注解
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// 导入项目自定义的实体类
import org.zerolg.aidemo2.entity.DocumentChunk;
import org.zerolg.aidemo2.entity.DocumentFile;
// 导入数据访问层接口
import org.zerolg.aidemo2.mapper.DocumentChunkMapper;
import org.zerolg.aidemo2.mapper.DocumentFileMapper;
import org.zerolg.aidemo2.mapper.DocumentMapper;
import org.zerolg.aidemo2.mapper.VectorStoreMapper;
// 导入项目自定义的模型类
import org.zerolg.aidemo2.model.IngestionStatus;
import org.zerolg.aidemo2.model.ParsedDocument;
// 导入文本分割器和工具类
import org.zerolg.aidemo2.support.splitter.SmartTextSplitter;
import org.zerolg.aidemo2.utils.HashUtils;

// 导入Java标准库
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库服务类
 * <p>
 * 这是系统的核心服务之一，负责管理知识库的所有操作
 * 主要功能包括：
 * 1. 文档摄入（Document Ingestion）- 将文档解析、切片并存储到向量数据库
 * 2. 文档管理 - 增删改查文档记录
 * 3. 向量存储管理 - 管理文档的向量化表示
 * 4. 去重处理 - 文件级和切片级的重复检测
 * 5. 数据清理 - 清理孤立的向量数据
 * <p>
 * 技术特点：
 * - 支持RAG（检索增强生成）架构
 * - 双写机制：同时写入关系型数据库和向量数据库
 * - 智能去重：避免重复存储相同内容
 * - 事务管理：确保数据一致性
 * - 异步处理：支持大文件的异步摄入
 */
@Service // Spring注解：标记这是一个服务层组件
public class KnowledgeBaseService {

    // 创建日志记录器，用于记录服务操作过程
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);

    // 依赖注入的各种数据访问层接口和服务
    private final DocumentFileMapper documentFileMapper; // 文档文件数据访问接口
    private final DocumentChunkMapper documentChunkMapper; // 文档切片数据访问接口
    private final DocumentMapper documentMapper; // 文档数据访问接口
    private final VectorStoreMapper vectorStoreMapper; // 向量存储数据访问接口
    private final TikaDocumentParser tikaDocumentParser; // Apache Tika文档解析器
    private final SmartTextSplitter smartTextSplitter; // 智能文本分割器
    private final VectorStore vectorStore; // Spring AI向量存储接口
    private final KnowledgeIngestionService ingestionService; // 知识摄入服务

    /**
     * 构造函数 - 依赖注入
     *
     * Spring会自动注入所需的依赖，这种方式比@Autowired更推荐
     * 因为它确保了依赖的不可变性和更好的测试支持
     */
    public KnowledgeBaseService(DocumentFileMapper documentFileMapper,
                                DocumentChunkMapper documentChunkMapper,
                                DocumentMapper documentMapper,
                                VectorStoreMapper vectorStoreMapper,
                                TikaDocumentParser tikaDocumentParser,
                                SmartTextSplitter smartTextSplitter,
                                VectorStore vectorStore,
                                KnowledgeIngestionService ingestionService) {
        this.documentFileMapper = documentFileMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.documentMapper = documentMapper;
        this.vectorStoreMapper = vectorStoreMapper;
        this.tikaDocumentParser = tikaDocumentParser;
        this.smartTextSplitter = smartTextSplitter;
        this.vectorStore = vectorStore;
        this.ingestionService = ingestionService;
    }

    /**
     * 摄入文档到知识库
     * <p>
     * 这是知识库的核心方法，完整的文档摄入流程包括：
     * 1. 文件级去重 - 通过文件哈希值避免重复处理相同文件
     * 2. 文档解析 - 使用Apache Tika解析各种格式的文档
     * 3. 智能切片 - 将长文档分割成适合向量化的小片段
     * 4. 切片级去重 - 避免存储重复的文本片段
     * 5. 双写操作 - 同时写入关系型数据库和向量数据库
     * 6. 向量化存储 - 生成文本的向量表示并存储
     *
     * @param ingestionId 摄入任务ID，用于跟踪处理进度
     * @param filePath    文件路径，指向要处理的文档文件
     * @param metadata    文档元数据，包含文件名、MIME类型等信息
     * @throws Exception 如果摄入过程中发生错误
     */
    @Transactional(rollbackFor = Exception.class) // 事务注解：确保操作的原子性，出现异常时回滚
    public void ingestDocument(String ingestionId, String filePath, Map<String, Object> metadata) throws Exception {
        // 1. 文件级去重
        // 计算文件的SHA-256哈希值，用于唯一标识文件内容
        String fileMd5 = HashUtils.getSha256(filePath);
        // 检查数据库中是否已存在相同哈希值且状态为已完成的文件
        boolean exists = documentFileMapper.exists(new LambdaQueryWrapper<DocumentFile>()
                .eq(DocumentFile::getFileHash, fileMd5) // 哈希值相等
                .eq(DocumentFile::getStatus, "COMPLETED")); // 状态为已完成

        if (exists) {
            // 如果文件已存在，记录日志并更新摄入状态为已完成
            logger.info("文件级去重命中: {}, MD5: {}", filePath, fileMd5);
            ingestionService.updateStatus(ingestionId, IngestionStatus.COMPLETED, 100, "文件已存在，跳过处理");
            return; // 直接返回，不进行后续处理
        }

        // 2. Tika 解析
        // 更新摄入状态为处理中，进度10%
        ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 10, "开始解析文档...");
        // 使用Apache Tika解析文档，提取文本内容和元数据
        ParsedDocument parsedDoc = tikaDocumentParser.parseDocument(filePath);
        String text = parsedDoc.getContent(); // 获取解析出的文本内容
        metadata.putAll(parsedDoc.getMetadata()); // 合并解析出的元数据

        // 添加文件上传标识，用于区分不同来源的文档
        metadata.put("source", "file_upload");

        // 检查文档内容是否为空
        if (text == null || text.isBlank()) {
            logger.warn("文档内容为空，任务ID: {}", ingestionId);
            ingestionService.updateStatus(ingestionId, IngestionStatus.FAILED, 0, "文档内容为空");
            return; // 内容为空则终止处理
        }

        // 3. 智能切片
        // 更新摄入状态，进度30%
        ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 30, "文档解析完成，正在切片...");
        // 使用智能文本分割器将长文档分割成多个片段
        List<String> chunks = smartTextSplitter.split(text);
        logger.info("切片完成，生成 {} 个片段", chunks.size());

        // 3.5 先插入 document 记录（满足外键约束）
        // 创建文档实体对象
        org.zerolg.aidemo2.entity.Document doc = new org.zerolg.aidemo2.entity.Document();
        doc.setId(ingestionId); // 使用摄入ID作为文档ID
        doc.setTitle((String) metadata.getOrDefault("filename", "unknown")); // 设置文档标题
        doc.setFilePath(filePath); // 设置文件路径
        doc.setMimeType((String) metadata.get("mime_type")); // 设置MIME类型
        doc.setMetadata(metadata); // 设置元数据
        doc.setChunkCount(chunks.size()); // 设置切片数量
        doc.setTotalTokens(text.length()); // 设置总token数（这里简单使用字符数）
        doc.setCreatedAt(OffsetDateTime.now()); // 设置创建时间
        doc.setUpdatedAt(OffsetDateTime.now()); // 设置更新时间
        doc.setIsDeleted(false); // 设置删除标志为false
        documentMapper.insert(doc); // 插入文档记录到数据库
        logger.info("已创建文档记录: id={}, title={}", ingestionId, doc.getTitle());

        // 4. 切片级去重
        // 更新摄入状态，进度60%
        ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 60, "切片完成，正在检查重复项...");
        // 为每个切片计算哈希值，用于去重
        List<String> chunkHashes = chunks.stream().map(HashUtils::getSha256).toList();
        List<String> existingHashes = new ArrayList<>(); // 存储已存在的哈希值
        if (!chunkHashes.isEmpty()) {
            // 查询数据库中已存在的哈希值
            existingHashes = vectorStoreMapper.selectExistingHashes(chunkHashes);
        }

        // 5. 双写操作 (写入 document_chunk 和 vector_store)
        // 准备新的AI文档列表，用于向量化存储
        List<org.springframework.ai.document.Document> newAiDocuments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i); // 当前切片的文本内容
            String chunkHash = chunkHashes.get(i); // 当前切片的哈希值

            // 如果切片已存在，跳过处理
            if (existingHashes.contains(chunkHash)) {
                logger.debug("切片级去重命中，跳过: hash={}", chunkHash);
                continue;
            }

            // 为新切片生成唯一ID
            String chunkId = UUID.randomUUID().toString();

            // 5.1 写入关系型表 (document_chunk)
            // 准备切片的元数据
            Map<String, Object> chunkMetaForDb = new HashMap<>(metadata);
            chunkMetaForDb.put("chunk_hash", chunkHash); // 添加切片哈希值

            // 创建文档切片实体
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(chunkId); // 设置切片ID
            chunk.setDocumentId(ingestionId); // 使用 ingestionId 作为 documentId
            chunk.setContent(chunkText); // 设置切片内容
            chunk.setChunkIndex(i); // 设置切片索引
            chunk.setTokenCount(chunkText.length()); // 设置token数量
            chunk.setCreatedAt(OffsetDateTime.now()); // 设置创建时间
            chunk.setMetadata(chunkMetaForDb); // 设置元数据
            documentChunkMapper.insert(chunk); // 插入到数据库

            // 5.2 准备写入向量表
            // 为向量存储准备元数据
            Map<String, Object> chunkMetaForVector = new HashMap<>(metadata);
            chunkMetaForVector.put("document_id", ingestionId); // 文档ID
            chunkMetaForVector.put("chunk_index", i); // 切片索引
            chunkMetaForVector.put("chunk_hash", chunkHash); // 切片哈希值
            // 添加文件信息用于引用和检索
            chunkMetaForVector.put("filename", metadata.getOrDefault("filename", "unknown"));
            chunkMetaForVector.put("source_filename", metadata.getOrDefault("filename", "unknown"));
            chunkMetaForVector.put("file_path", filePath);
            chunkMetaForVector.put("source_document_id", ingestionId);
            chunkMetaForVector.put("source_chunk_index", i);
            chunkMetaForVector.put("source_mime_type", metadata.get("mime_type"));

            // 创建Spring AI文档对象，用于向量化
            org.springframework.ai.document.Document aiDoc = new org.springframework.ai.document.Document(chunkId, chunkText, chunkMetaForVector);
            newAiDocuments.add(aiDoc);
        }

        // 6. 向量化并入库
        if (!newAiDocuments.isEmpty()) {
            // 更新摄入状态，进度80%
            ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 80, "正在生成向量并入库...");
            // 将文档添加到向量存储（这会自动进行向量化）
            vectorStore.add(newAiDocuments);
            logger.info("向量化入库成功: {} 个新切片", newAiDocuments.size());
        } else {
            logger.info("所有切片均已存在，无需入库");
        }

        // 7. 记录文件处理成功
        // 创建文档文件记录，标记文件已成功处理
        DocumentFile docFile = DocumentFile.builder()
                .fileHash(fileMd5) // 文件哈希值
                .filename((String) metadata.getOrDefault("filename", "unknown")) // 文件名
                .status("COMPLETED") // 状态为已完成
                .createTime(LocalDateTime.now()) // 创建时间
                .build();
        documentFileMapper.insert(docFile); // 插入到数据库

        // 8. 更新最终状态
        // 更新摄入状态为已完成，进度100%
        ingestionService.updateStatus(ingestionId, IngestionStatus.COMPLETED, 100, "处理成功");
    }

    /**
     * 纯文本直接摄入 (用于 /ingest 接口)
     * 跳过文件去重和 Tika 解析，直接切片入库
     *
     * @param title    文档标题
     * @param content  纯文本内容
     * @param metadata 额外元数据
     * @return 生成的 documentId (这里使用 UUID)
     */
    @Transactional(rollbackFor = Exception.class)
    public String ingest(String title, String content, Map<String, Object> metadata) {
        String documentId = UUID.randomUUID().toString();

        // 补充元数据
        if (metadata == null) metadata = new HashMap<>();
        metadata.put("title", title);
        metadata.put("source", "manual_ingest");

        // 0. 先插入 document 记录（满足外键约束）
        org.zerolg.aidemo2.entity.Document doc = new org.zerolg.aidemo2.entity.Document();
        doc.setId(documentId);
        doc.setTitle(title);
        doc.setMimeType((String) metadata.get("mime_type"));
        // 手动摄入的文档不设置文件路径，因为没有对应的物理文件
        doc.setFilePath(null);
        doc.setMetadata(metadata);
        doc.setCreatedAt(OffsetDateTime.now());
        doc.setUpdatedAt(OffsetDateTime.now());
        doc.setIsDeleted(false);
        
        // 1. 智能切片
        List<String> chunks = smartTextSplitter.split(content);
        logger.info("文本切片完成，生成 {} 个片段", chunks.size());

        // 设置切片数量和总 token 数
        doc.setChunkCount(chunks.size());
        doc.setTotalTokens(content.length());

        // 插入 document 记录
        documentMapper.insert(doc);
        logger.info("已创建文档记录: id={}, title={}", documentId, title);

        // 2. 切片级去重
        List<String> chunkHashes = chunks.stream().map(HashUtils::getSha256).toList();
        List<String> existingHashes = new ArrayList<>();
        if (!chunkHashes.isEmpty()) {
            existingHashes = vectorStoreMapper.selectExistingHashes(chunkHashes);
        }

        // 3. 双写操作
        List<org.springframework.ai.document.Document> newAiDocuments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            String chunkHash = chunkHashes.get(i);

            if (existingHashes.contains(chunkHash)) {
                continue;
            }

            String chunkId = UUID.randomUUID().toString();

            // 3.1 写入关系型表
            Map<String, Object> chunkMeta = new HashMap<>(metadata);
            chunkMeta.put("chunk_hash", chunkHash);

            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(chunkId);
            chunk.setDocumentId(documentId);
            chunk.setContent(chunkText);
            chunk.setChunkIndex(i);
            chunk.setTokenCount(chunkText.length());
            chunk.setCreatedAt(OffsetDateTime.now());
            chunk.setMetadata(chunkMeta);
            documentChunkMapper.insert(chunk);

            // 3.2 准备写入向量表
            Map<String, Object> vectorMeta = new HashMap<>(metadata);
            vectorMeta.put("document_id", documentId);
            vectorMeta.put("chunk_index", i);
            vectorMeta.put("chunk_hash", chunkHash);
            // 添加文件信息用于引用
            vectorMeta.put("filename", title);
            vectorMeta.put("source_filename", title);
            vectorMeta.put("source_document_id", documentId);
            vectorMeta.put("source_chunk_index", i);
            vectorMeta.put("source_mime_type", metadata.get("mime_type"));

            newAiDocuments.add(new org.springframework.ai.document.Document(chunkId, chunkText, vectorMeta));
        }

        // 4. 向量入库
        if (!newAiDocuments.isEmpty()) {
            vectorStore.add(newAiDocuments);
            logger.info("纯文本向量化入库成功: {} 个新切片", newAiDocuments.size());
        }

        return documentId;
    }

    /**
     * 删除文档及其所有相关数据
     * 包括：文档记录、切片记录、向量数据、物理文件
     *
     * @param documentId 文档ID
     * @return 删除结果信息
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteDocument(String documentId) {
        logger.info("开始删除文档: documentId={}", documentId);

        Map<String, Object> result = new HashMap<>();
        result.put("documentId", documentId);

        try {
            // 1. 查询文档信息
            org.zerolg.aidemo2.entity.Document document = documentMapper.selectById(documentId);
            if (document == null) {
                result.put("status", "error");
                result.put("message", "文档不存在");
                return result;
            }

            logger.info("找到文档: title={}, filePath={}", document.getTitle(), document.getFilePath());

            // 2. 查询文档的所有切片
            List<DocumentChunk> chunks = documentChunkMapper.selectList(
                    new LambdaQueryWrapper<DocumentChunk>()
                            .eq(DocumentChunk::getDocumentId, documentId)
            );

            logger.info("找到 {} 个文档切片", chunks.size());

            // 3. 删除向量数据
            int vectorDeleteCount = 0;
            if (!chunks.isEmpty()) {
                // 方法1: 根据 document_id 删除
                vectorDeleteCount = vectorStoreMapper.deleteByDocumentId(documentId);
                logger.info("删除向量数据: {} 条记录", vectorDeleteCount);

                // 方法2: 如果方法1失败，尝试根据 chunk_id 删除
                if (vectorDeleteCount == 0) {
                    List<String> chunkIds = chunks.stream()
                            .map(DocumentChunk::getId)
                            .collect(Collectors.toList());
                    vectorDeleteCount = vectorStoreMapper.deleteByChunkIds(chunkIds);
                    logger.info("通过 chunk_id 删除向量数据: {} 条记录", vectorDeleteCount);
                }
            }

            // 4. 删除文档切片记录
            int chunkDeleteCount = documentChunkMapper.delete(
                    new LambdaQueryWrapper<DocumentChunk>()
                            .eq(DocumentChunk::getDocumentId, documentId)
            );
            logger.info("删除文档切片记录: {} 条记录", chunkDeleteCount);

            // 5. 删除文档记录
            int documentDeleteCount = documentMapper.deleteById(documentId);
            logger.info("删除文档记录: {} 条记录", documentDeleteCount);

            // 6. 删除物理文件（可选）
            String filePath = document.getFilePath();
            boolean fileDeleted = false;
            if (filePath != null && !filePath.trim().isEmpty()) {
                try {
                    Path path = Paths.get(filePath);
                    if (Files.exists(path)) {
                        Files.delete(path);
                        fileDeleted = true;
                        logger.info("删除物理文件成功: {}", filePath);
                    } else {
                        logger.warn("物理文件不存在: {}", filePath);
                    }
                } catch (Exception e) {
                    logger.warn("删除物理文件失败: {}", filePath, e);
                }
            }

            // 7. 构建删除结果
            result.put("status", "success");
            result.put("message", "文档删除成功");
            result.put("details", Map.of(
                    "documentTitle", document.getTitle(),
                    "chunksDeleted", chunkDeleteCount,
                    "vectorsDeleted", vectorDeleteCount,
                    "documentDeleted", documentDeleteCount,
                    "fileDeleted", fileDeleted,
                    "filePath", filePath != null ? filePath : "无物理文件"
            ));

            logger.info("文档删除完成: documentId={}, chunks={}, vectors={}, file={}",
                    documentId, chunkDeleteCount, vectorDeleteCount, fileDeleted);

            return result;

        } catch (Exception e) {
            logger.error("删除文档失败: documentId={}", documentId, e);
            result.put("status", "error");
            result.put("message", "删除失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 批量删除文档
     *
     * @param documentIds 文档ID列表
     * @return 批量删除结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteDocuments(List<String> documentIds) {
        logger.info("开始批量删除文档: count={}", documentIds.size());

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();

        int successCount = 0;
        int failureCount = 0;

        for (String documentId : documentIds) {
            try {
                Map<String, Object> singleResult = deleteDocument(documentId);
                results.add(singleResult);

                if ("success".equals(singleResult.get("status"))) {
                    successCount++;
                } else {
                    failureCount++;
                }

            } catch (Exception e) {
                logger.error("批量删除中单个文档失败: documentId={}", documentId, e);
                results.add(Map.of(
                        "documentId", documentId,
                        "status", "error",
                        "message", "删除失败: " + e.getMessage()
                ));
                failureCount++;
            }
        }

        result.put("status", failureCount == 0 ? "success" : "partial");
        result.put("message", String.format("批量删除完成: 成功 %d 个, 失败 %d 个", successCount, failureCount));
        result.put("totalCount", documentIds.size());
        result.put("successCount", successCount);
        result.put("failureCount", failureCount);
        result.put("results", results);

        logger.info("批量删除完成: total={}, success={}, failure={}",
                documentIds.size(), successCount, failureCount);

        return result;
    }

    /**
     * 清理孤立的向量数据
     * 删除没有对应文档记录的向量数据
     *
     * @return 清理结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cleanupOrphanedVectors() {
        logger.info("开始清理孤立的向量数据...");

        // 这里需要实现复杂的 SQL 查询来找出孤立的向量数据
        // 暂时返回占位符结果
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "孤立向量数据清理功能待实现");
        result.put("cleanedCount", 0);

        return result;
    }
}