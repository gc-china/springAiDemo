package org.zerolg.aidemo2.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zerolg.aidemo2.entity.DocumentChunk;
import org.zerolg.aidemo2.entity.DocumentFile;
import org.zerolg.aidemo2.mapper.DocumentChunkMapper;
import org.zerolg.aidemo2.mapper.DocumentFileMapper;
import org.zerolg.aidemo2.mapper.DocumentMapper;
import org.zerolg.aidemo2.mapper.VectorStoreMapper;
import org.zerolg.aidemo2.model.IngestionStatus;
import org.zerolg.aidemo2.model.ParsedDocument;
import org.zerolg.aidemo2.support.splitter.SmartTextSplitter;
import org.zerolg.aidemo2.utils.HashUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final DocumentFileMapper documentFileMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMapper documentMapper;
    private final VectorStoreMapper vectorStoreMapper;
    private final TikaDocumentParser tikaDocumentParser;
    private final SmartTextSplitter smartTextSplitter;
    private final VectorStore vectorStore;
    private final KnowledgeIngestionService ingestionService;

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

    @Transactional(rollbackFor = Exception.class)
    public void ingestDocument(String ingestionId, String filePath, Map<String, Object> metadata) throws Exception {
        // 1. 文件级去重
        String fileMd5 = HashUtils.getSha256(filePath);
        boolean exists = documentFileMapper.exists(new LambdaQueryWrapper<DocumentFile>()
                .eq(DocumentFile::getFileHash, fileMd5)
                .eq(DocumentFile::getStatus, "COMPLETED"));

        if (exists) {
            logger.info("文件级去重命中: {}, MD5: {}", filePath, fileMd5);
            ingestionService.updateStatus(ingestionId, IngestionStatus.COMPLETED, 100, "文件已存在，跳过处理");
            return;
        }

        // 2. Tika 解析
        ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 10, "开始解析文档...");
        ParsedDocument parsedDoc = tikaDocumentParser.parseDocument(filePath);
        String text = parsedDoc.getContent();
        metadata.putAll(parsedDoc.getMetadata());

        // 添加文件上传标识
        metadata.put("source", "file_upload");

        if (text == null || text.isBlank()) {
            logger.warn("文档内容为空，任务ID: {}", ingestionId);
            ingestionService.updateStatus(ingestionId, IngestionStatus.FAILED, 0, "文档内容为空");
            return;
        }

        // 3. 智能切片
        ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 30, "文档解析完成，正在切片...");
        List<String> chunks = smartTextSplitter.split(text);
        logger.info("切片完成，生成 {} 个片段", chunks.size());

        // 3.5 先插入 document 记录（满足外键约束）
        org.zerolg.aidemo2.entity.Document doc = new org.zerolg.aidemo2.entity.Document();
        doc.setId(ingestionId);
        doc.setTitle((String) metadata.getOrDefault("filename", "unknown"));
        doc.setFilePath(filePath);
        doc.setMimeType((String) metadata.get("mime_type"));
        doc.setMetadata(metadata);
        doc.setChunkCount(chunks.size());
        doc.setTotalTokens(text.length());
        doc.setCreatedAt(OffsetDateTime.now());
        doc.setUpdatedAt(OffsetDateTime.now());
        doc.setIsDeleted(false);
        documentMapper.insert(doc);
        logger.info("已创建文档记录: id={}, title={}", ingestionId, doc.getTitle());

        // 4. 切片级去重
        ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 60, "切片完成，正在检查重复项...");
        List<String> chunkHashes = chunks.stream().map(HashUtils::getSha256).toList();
        List<String> existingHashes = new ArrayList<>();
        if (!chunkHashes.isEmpty()) {
            existingHashes = vectorStoreMapper.selectExistingHashes(chunkHashes);
        }

        // 5. 双写操作 (写入 document_chunk 和 vector_store)
        List<org.springframework.ai.document.Document> newAiDocuments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            String chunkHash = chunkHashes.get(i);

            if (existingHashes.contains(chunkHash)) {
                logger.debug("切片级去重命中，跳过: hash={}", chunkHash);
                continue;
            }

            String chunkId = UUID.randomUUID().toString();

            // 5.1 写入关系型表
            Map<String, Object> chunkMetaForDb = new HashMap<>(metadata);
            chunkMetaForDb.put("chunk_hash", chunkHash);

            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(chunkId);
            chunk.setDocumentId(ingestionId); // 使用 ingestionId 作为 documentId
            chunk.setContent(chunkText);
            chunk.setChunkIndex(i);
            chunk.setTokenCount(chunkText.length());
            chunk.setCreatedAt(OffsetDateTime.now());
            chunk.setMetadata(chunkMetaForDb);
            documentChunkMapper.insert(chunk);

            // 3.2 准备写入向量表
            Map<String, Object> chunkMetaForVector = new HashMap<>(metadata);
            chunkMetaForVector.put("document_id", ingestionId);
            chunkMetaForVector.put("chunk_index", i);
            chunkMetaForVector.put("chunk_hash", chunkHash);
            // 添加文件信息用于引用
            chunkMetaForVector.put("filename", metadata.getOrDefault("filename", "unknown"));
            chunkMetaForVector.put("source_filename", metadata.getOrDefault("filename", "unknown"));
            chunkMetaForVector.put("file_path", filePath);
            chunkMetaForVector.put("source_document_id", ingestionId);
            chunkMetaForVector.put("source_chunk_index", i);
            chunkMetaForVector.put("source_mime_type", metadata.get("mime_type"));

            org.springframework.ai.document.Document aiDoc = new org.springframework.ai.document.Document(chunkId, chunkText, chunkMetaForVector);
            newAiDocuments.add(aiDoc);
        }

        // 6. 向量化并入库
        if (!newAiDocuments.isEmpty()) {
            ingestionService.updateStatus(ingestionId, IngestionStatus.PROCESSING, 80, "正在生成向量并入库...");
            vectorStore.add(newAiDocuments);
            logger.info("向量化入库成功: {} 个新切片", newAiDocuments.size());
        } else {
            logger.info("所有切片均已存在，无需入库");
        }

        // 7. 记录文件处理成功
        DocumentFile docFile = DocumentFile.builder()
                .fileHash(fileMd5)
                .filename((String) metadata.getOrDefault("filename", "unknown"))
                .status("COMPLETED")
                .createTime(LocalDateTime.now())
                .build();
        documentFileMapper.insert(docFile);

        // 8. 更新最终状态
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
}