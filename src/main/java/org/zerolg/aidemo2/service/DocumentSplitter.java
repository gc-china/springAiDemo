package org.zerolg.aidemo2.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档切分服务
 * 负责将长文档切分为适合 Embedding 的小片段
 */
@Service
public class DocumentSplitter {

    // 默认切片大小 (Tokens)
    private static final int DEFAULT_CHUNK_SIZE = 500;
    // 默认重叠大小 (Tokens)
    private static final int DEFAULT_OVERLAP = 50;

    /**
     * 切分文本
     * @param text 原始文本
     * @return 切分后的文本列表
     */
    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * 切分文本 (自定义参数)
     * @param text 原始文本
     * @param chunkSize 切片大小
     * @param overlap 重叠大小
     * @return 切分后的文本列表
     */
    public List<String> split(String text, int chunkSize, int overlap) {
        // 使用 Spring AI 内置的 TokenTextSplitter
        TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, overlap, 5, 10000, true);
        
        // 将文本包装为 Document，然后切分
        Document doc = new Document(text);
        List<Document> chunks = splitter.split(doc);
        
        // 提取文本内容
        return chunks.stream()
                .map(Document::getText)
                .collect(Collectors.toList());
    }
}
