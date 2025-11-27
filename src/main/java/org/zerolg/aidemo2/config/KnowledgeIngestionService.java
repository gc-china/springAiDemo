package org.zerolg.aidemo2.config;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeIngestionService {

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
            System.out.println("🚀 正在加载知识库...");
            TextReader reader = new TextReader(policyResource);
            List<Document> documents = reader.get();
            List<Document> splitDocs = textSplitter.split(documents); // M3 方法名是 split
            vectorStore.add(splitDocs);
            System.out.println("✅ 知识库加载完成！");
        } catch (Exception e) {
            System.out.println("⚠️ 知识库加载跳过 (文件可能不存在): " + e.getMessage());
        }
    }
}
