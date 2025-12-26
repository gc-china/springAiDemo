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
import org.zerolg.aidemo2.entity.DocumentChunk;
import org.zerolg.aidemo2.mapper.DocumentChunkMapper;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 服务 (商业化增强版)
 * 核心能力：
 * 1. 混合检索 (Hybrid Search): 向量检索 (语义) + 关键词检索 (精确匹配)
 * 2. RRF 融合 (Reciprocal Rank Fusion): 科学合并两路召回结果
 * 3. LLM 重排序 (Rerank): 使用大模型进行最终的相关性精排
 */
@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    // RRF 算法常数 k，工业界通常取 60
    private static final double RRF_K = 60.0;

    @Value("classpath:/static/rerank-prompt.st")
    private Resource rerankPromptResource;

    // RAG 检索配置参数
    @Value("${ai.rag.topK:8}")
    private int ragTopK;

    @Value("${ai.rag.similarityThreshold:0.4}")
    private double ragSimilarityThreshold;
    // 新增：注入 Mapper 用于全文检索
    private final DocumentChunkMapper documentChunkMapper;

    public RagService(ChatClient chatClient, VectorStore vectorStore, DocumentChunkMapper documentChunkMapper) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.documentChunkMapper = documentChunkMapper;
    }

    /**
     * 执行混合检索并重排序 (Hybrid Retrieve and Rerank)
     *
     * @param query 用户查询
     * @return 精选后的文档列表
     */
    public Mono<List<Document>> retrieveAndRerank(String query) {
        // 1. 并行执行双路召回 (Vector + Keyword)

        // 路一：向量检索 (语义召回)
        Mono<List<Document>> vectorSearch = Mono.fromCallable(() -> {
            logger.debug("🔍 [1/3] 执行向量检索, query: {}", query);
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(ragTopK)
                    .similarityThreshold(ragSimilarityThreshold)
                    .build();
            return vectorStore.similaritySearch(searchRequest);
        }).subscribeOn(Schedulers.boundedElastic());

        // 路二：全文检索 (关键词精确召回)
        Mono<List<Document>> keywordSearch = Mono.fromCallable(() -> {
            logger.debug("🔍 [1/3] 执行全文检索, query: {}", query);
            return searchByKeyword(query, ragTopK);
        }).subscribeOn(Schedulers.boundedElastic());

        // 2. 合并结果并应用 RRF 算法
        return Mono.zip(vectorSearch, keywordSearch)
                .map(tuple -> {
                    List<Document> vectorDocs = tuple.getT1();
                    List<Document> keywordDocs = tuple.getT2();
                    logger.debug("📊 召回统计: 向量={}条, 关键词={}条", vectorDocs.size(), keywordDocs.size());
                    return applyRRF(vectorDocs, keywordDocs);
                })
                .flatMap(fusedDocs -> {
                    logger.debug("🤝 [2/3] RRF 融合完成，保留 Top {} 个候选文档，开始重排序...", fusedDocs.size());
                    // 3. LLM 重排序 (Re-ranking) - 专家评审
                    return rerankDocuments(query, fusedDocs);
                });
    }

    /**
     * 辅助方法：调用 Mapper 进行全文检索并转换为 Spring AI Document
     */
    private List<Document> searchByKeyword(String query, int limit) {
        try {
            List<DocumentChunk> chunks = documentChunkMapper.searchByKeyword(query, limit);
            return chunks.stream()
                    .map(chunk -> {
                        // 构建 Document 对象，确保 ID 一致以便去重
                        // 注意：这里假设 metadata 不为空，如果为空需要处理 null
                        Map<String, Object> metadata = chunk.getMetadata();
                        if (metadata == null) metadata = new HashMap<>();
                        return new Document(chunk.getId().toString(), chunk.getContent(), metadata);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("全文检索失败 (降级处理，不影响主流程)", e);
            return Collections.emptyList();
        }
    }

    /**
     * RRF (Reciprocal Rank Fusion) 倒数排名融合算法
     * score = 1 / (k + rank_i)
     */
    private List<Document> applyRRF(List<Document> vectorDocs, List<Document> keywordDocs) {
        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, Document> docContentMap = new HashMap<>();

        // 1. 计算向量检索得分
        for (int i = 0; i < vectorDocs.size(); i++) {
            Document doc = vectorDocs.get(i);
            String id = doc.getId();
            docContentMap.putIfAbsent(id, doc);
            scoreMap.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
        }

        // 2. 计算全文检索得分
        for (int i = 0; i < keywordDocs.size(); i++) {
            Document doc = keywordDocs.get(i);
            String id = doc.getId();
            docContentMap.putIfAbsent(id, doc);
            scoreMap.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
        }

        // 3. 按 RRF 得分降序排序，并适当扩大候选集给 Reranker
        // 这里我们取 2倍 topK 的数量，或者最多 16 个，避免给 LLM 太多 token
        long limit = Math.min(ragTopK * 2L, 16);

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> docContentMap.get(entry.getKey()))
                .collect(Collectors.toList());
    }

    /**
     * 使用 LLM 进行重排序 (Listwise Reranking) - 响应式
     * 完整保留，未省略任何代码
     */
    private Mono<List<Document>> rerankDocuments(String query, List<Document> documents) {
        if (documents.isEmpty()) {
            return Mono.just(new ArrayList<>());
        }

        return Mono.fromCallable(() -> {
                    // 限制 Rerank 的最大文档数，防止 Context 超限 (例如限制为 10 个)
                    // 虽然 RRF 已经过滤了一次，这里做个兜底
                    List<Document> candidates = documents.size() > 10 ? documents.subList(0, 10) : documents;

                    // 构建重排序 Prompt Context
                    StringBuilder docsBuilder = new StringBuilder();
                    for (int i = 0; i < candidates.size(); i++) {
                        // 使用 formattedContent 包含元数据信息，有助于 LLM 判断
                        docsBuilder.append("[").append(i).append("] ").append(candidates.get(i).getFormattedContent()).append("\n");
                    }

                    // 加载 Prompt 模板
                    PromptTemplate promptTemplate = new PromptTemplate(rerankPromptResource);
                    String rerankPrompt = promptTemplate.render(Map.of(
                            "query", query,
                            "documents", docsBuilder.toString(),
                            "maxIndex", candidates.size() - 1
                    ));

                    // 使用 BeanOutputConverter 处理 JSON 解析
                    BeanOutputConverter<List<Integer>> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<Integer>>() {
                    });

                    // 调用 LLM 获取评审结果 (阻塞操作)
                    // 建议：对于 Rerank，temperature 设为 0 以获得最稳定的结果
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
                        if (index >= 0 && index < candidates.size()) {
                            rerankedDocs.add(candidates.get(index));
                        }
                    }

                    logger.debug("✅ [3/3] 重排序完成，保留了 {}/{} 个文档", rerankedDocs.size(), candidates.size());
                    return rerankedDocs;
                })
                .subscribeOn(Schedulers.boundedElastic()) // 确保 LLM 调用不阻塞主线程
                .onErrorResume(e -> {
                    // 降级策略
                    logger.warn("⚠️ 重排序失败，降级使用 RRF 排序的前 3 个文档: {}", e.getMessage());
                    return Mono.just(documents.stream().limit(3).collect(Collectors.toList()));
                });
    }
}