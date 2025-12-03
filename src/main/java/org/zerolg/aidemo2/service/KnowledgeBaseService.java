package org.zerolg.aidemo2.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zerolg.aidemo2.entity.DocumentChunk;
import org.zerolg.aidemo2.mapper.DocumentChunkMapper;
import org.zerolg.aidemo2.mapper.DocumentMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeBaseService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentSplitter documentSplitter;
    private final VectorStore vectorStore;

    public KnowledgeBaseService(DocumentMapper documentMapper,
            DocumentChunkMapper documentChunkMapper,
            DocumentSplitter documentSplitter,
            VectorStore vectorStore) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.documentSplitter = documentSplitter;
        this.vectorStore = vectorStore;
    }

    /**
     * 摄入文档
     * 
     * @param title    文档标题
     * @param content  文档内容
     * @param metadata 元数据
     * @return 文档ID
     */
    @Transactional
    public String ingest(String title, String content, Map<String, Object> metadata) {
        // 1. 保存文档元数据
        org.zerolg.aidemo2.entity.Document doc = new org.zerolg.aidemo2.entity.Document();
        doc.setTitle(title);
        doc.setMetadata(metadata);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        doc.setIsDeleted(false);
        // 估算总 Token (简单按字符)
        doc.setTotalTokens(content.length() / 2);

        documentMapper.insert(doc);
        String documentId = doc.getId();

        // 2. 切分文档
        List<String> segments = documentSplitter.split(content);
        doc.setChunkCount(segments.size());
        documentMapper.updateById(doc);

        List<Document> aiDocuments = new ArrayList<>();

        // 3. 保存切片并准备向量化
        for (int i = 0; i < segments.size(); i++) {
            String segmentContent = segments.get(i);

            // 3.1 保存到 document_chunk 表 (用于管理和展示)
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setContent(segmentContent);
            chunk.setChunkIndex(i);
            chunk.setTokenCount(segmentContent.length() / 2); // 简单估算
            chunk.setCreatedAt(LocalDateTime.now());

            Map<String, Object> chunkMeta = new HashMap<>();
            chunkMeta.put("index", i);
            chunk.setMetadata(chunkMeta);

            documentChunkMapper.insert(chunk);

            // 3.2 构建 Spring AI Document (用于向量存储)
            Map<String, Object> aiMeta = new HashMap<>(metadata != null ? metadata : new HashMap<>());
            aiMeta.put("document_id", documentId);
            aiMeta.put("chunk_id", chunk.getId());
            aiMeta.put("chunk_index", i);
            aiMeta.put("title", title);

            Document aiDoc = new Document(segmentContent, aiMeta);
            aiDocuments.add(aiDoc);
        }

        // 4. 写入向量数据库 (生成 Embedding 并保存)
        vectorStore.add(aiDocuments);

        return documentId;
    }
}
