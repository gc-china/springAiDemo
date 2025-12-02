package org.zerolg.aidemo2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 服务 - 负责文档检索与重排序
 * 符合单一职责原则，将检索逻辑从主业务流中剥离
 */
@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("classpath:/static/rerank-prompt.st")
    private Resource rerankPromptResource;

    // RAG 检索配置参数
    @Value("${ai.rag.topK:8}")
    private int ragTopK;

    @Value("${ai.rag.similarityThreshold:0.4}")
    private double ragSimilarityThreshold;

    public RagService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    /**
     * 执行检索并重排序 (Retrieve and Rerank)
     * 
     * @param query 用户查询
     * @return 精选后的文档列表
     */
    public Mono<List<Document>> retrieveAndRerank(String query) {
        // 1. RAG 检索 (Retrieve) - 扩大范围 (Recall)
        // 将阻塞的 VectorStore 操作包装在 elastic 调度器中
        return Mono.fromCallable(() -> {
            logger.debug("正在执行向量检索, query: {}", query);
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(ragTopK)
                    .similarityThreshold(ragSimilarityThreshold)
                    .build();
            return vectorStore.similaritySearch(searchRequest);
        })
        .subscribeOn(Schedulers.boundedElastic()) // 确保在 IO 线程池执行
        .flatMap(initialDocuments -> {
            logger.debug("检索到 {} 个文档，开始重排序...", initialDocuments.size());
            // 2. 重排序 (Re-ranking) - 专家评审
            return rerankDocuments(query, initialDocuments);
        });
    }

    /**
     * 使用 LLM 进行重排序 (Listwise Reranking) - 响应式
     */
    private Mono<List<Document>> rerankDocuments(String query, List<Document> documents) {
        if (documents.isEmpty()) {
            return Mono.just(new ArrayList<>());
        }

        return Mono.fromCallable(() -> {
            // 构建重排序 Prompt Context
            StringBuilder docsBuilder = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                docsBuilder.append("[").append(i).append("] ").append(documents.get(i).getFormattedContent()).append("\n");
            }

            // 加载 Prompt 模板
            PromptTemplate promptTemplate = new PromptTemplate(rerankPromptResource);
            String rerankPrompt = promptTemplate.render(Map.of(
                "query", query,
                "documents", docsBuilder.toString(),
                "maxIndex", documents.size() - 1
            ));

            // 使用 BeanOutputConverter 处理 JSON 解析
            BeanOutputConverter<List<Integer>> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<Integer>>() {});
            
            // 调用 LLM 获取评审结果 (阻塞操作)
            String response = chatClient.prompt()
                    .user(rerankPrompt)
                    .call()
                    .content();
            
            // 转换
            List<Integer> selectedIndices = converter.convert(response);
            
            if (selectedIndices == null) {
                selectedIndices = new ArrayList<>();
            }

            // 根据索引构建最终列表
            List<Document> rerankedDocs = new ArrayList<>();
            for (Integer index : selectedIndices) {
                if (index >= 0 && index < documents.size()) {
                    rerankedDocs.add(documents.get(index));
                }
            }
            
            logger.debug("重排序完成，保留了 {}/{} 个文档", rerankedDocs.size(), documents.size());
            return rerankedDocs;
        })
        .subscribeOn(Schedulers.boundedElastic()) // 确保 LLM 调用不阻塞主线程
        .onErrorResume(e -> {
            // 降级策略
            logger.warn("重排序失败，降级使用前3个文档: {}", e.getMessage());
            return Mono.just(documents.stream().limit(3).collect(Collectors.toList()));
        });
    }
}