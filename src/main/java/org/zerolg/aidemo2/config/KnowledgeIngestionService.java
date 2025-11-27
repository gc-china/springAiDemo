package org.zerolg.aidemo2.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class KnowledgeIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    @Value("classpath:company_policy.txt")
    private Resource policyResource;

    public KnowledgeIngestionService(VectorStore vectorStore, TokenTextSplitter textSplitter) {
        this.vectorStore = vectorStore;
        this.textSplitter = textSplitter;
    }

    @PostConstruct
    public void init() {
        try {
            // Check if we need to ingest data (simple check: if store is empty or file missing)
            // Ideally, we would check modification timestamps, but for now, we'll just check if the file exists
            File vectorStoreFile = new File("vectorstore.json");
            if (vectorStoreFile.exists()) {
                logger.info("Vector store file exists, skipping ingestion.");
                return;
            }

            logger.info("🚀 正在加载知识库...");
            TextReader reader = new TextReader(policyResource);
            List<Document> documents = reader.get();
            List<Document> splitDocs = textSplitter.split(documents);
            vectorStore.add(splitDocs);
            
            // Persist to file if it's a SimpleVectorStore
            if (vectorStore instanceof SimpleVectorStore) {
                ((SimpleVectorStore) vectorStore).save(vectorStoreFile);
                logger.info("Saved vector store to file: {}", vectorStoreFile.getAbsolutePath());
            }
            
            logger.info("✅ 知识库加载完成！");
        } catch (Exception e) {
            logger.warn("⚠️ 知识库加载跳过 (文件可能不存在): {}", e.getMessage());
        }
    }
}
