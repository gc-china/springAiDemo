package org.zerolg.aidemo2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.entity.DocumentChunk;
import org.zerolg.aidemo2.mapper.DocumentChunkMapper;
import org.zerolg.aidemo2.utils.NetworkUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

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
    private final ObjectMapper objectMapper;
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

    public RagService(ChatClient chatClient, VectorStore vectorStore, DocumentChunkMapper documentChunkMapper, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.documentChunkMapper = documentChunkMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行混合检索并重排序 (Hybrid Retrieve and Rerank)
     * 增强版：添加引用来源编号和文件信息
     *
     * @param query 用户查询
     * @return 精选后的文档列表（带引用编号）
     */
    public Mono<List<Document>> retrieveAndRerank(String query) {
        // 1. 并行执行双路召回 (Vector + Keyword)

        // 路一：向量检索 (语义召回)
        Mono<List<Document>> vectorSearch = Mono.fromCallable(() -> {
            logger.debug("🔍 [1/3] 执行向量检索, query: {}", query);
            try {
                SearchRequest searchRequest = SearchRequest.builder()
                        .query(query)
                        .topK(ragTopK)
                        .similarityThreshold(ragSimilarityThreshold)
                        .build();
                List<Document> docs = vectorStore.similaritySearch(searchRequest);
                // 增强元数据
                return enhanceDocumentsWithSourceInfo(docs, "vector_search");
            } catch (Exception e) {
                NetworkUtils.logNetworkException(logger, "向量检索", e);
                // 返回空列表，不影响整体流程
                return new ArrayList<Document>();
            }
        }).subscribeOn(Schedulers.boundedElastic());

        // 路二：全文检索 (关键词精确召回)
        Mono<List<Document>> keywordSearch = Mono.fromCallable(() -> {
            logger.debug("🔍 [1/3] 执行全文检索, query: {}", query);
            try {
                List<Document> docs = searchByKeyword(query, ragTopK);
                // 增强元数据
                return enhanceDocumentsWithSourceInfo(docs, "keyword_search");
            } catch (Exception e) {
                logger.warn("全文检索失败: {}", e.getMessage());
                // 返回空列表，不影响整体流程
                return new ArrayList<Document>();
            }
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
                })
                .onErrorResume(e -> {
                    logger.error("检索和重排序过程中发生异常，使用降级策略", e);
                    // 降级策略：返回基于关键词搜索的结果
                    try {
                        List<Document> fallbackDocs = searchByKeyword(query, Math.min(ragTopK, 3));
                        return Mono.just(enhanceDocumentsWithSourceInfo(fallbackDocs, "fallback_search"));
                    } catch (Exception fallbackException) {
                        logger.error("降级策略也失败，返回空结果", fallbackException);
                        return Mono.just(new ArrayList<Document>());
                    }
                })
                .map(this::addCitationNumbers); // 4. 添加引用编号
    }

    /**
     * 增强文档元数据，添加文件来源信息
     */
    private List<Document> enhanceDocumentsWithSourceInfo(List<Document> documents, String searchType) {
        return documents.stream().map(doc -> {
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());

            // 添加搜索类型
            metadata.put("search_type", searchType);

            // 确保文件信息完整，避免 null 值
            String filename = (String) metadata.getOrDefault("filename", "未知文件");
            // 修复：统一使用 source_document_id 字段
            String documentId = (String) metadata.get("source_document_id");
            if (documentId == null) {
                // 兼容旧的 document_id 字段
                documentId = (String) metadata.get("document_id");
            }
            Integer chunkIndex = (Integer) metadata.get("chunk_index");
            if (chunkIndex == null) {
                // 兼容 source_chunk_index 字段
                chunkIndex = (Integer) metadata.get("source_chunk_index");
            }
            String mimeType = (String) metadata.get("mime_type");
            if (mimeType == null) {
                // 兼容 source_mime_type 字段
                mimeType = (String) metadata.get("source_mime_type");
            }
            String source = (String) metadata.get("source");

            // 标准化文件信息，确保没有 null 值
            metadata.put("source_filename", filename != null ? filename : "未知文件");
            metadata.put("source_document_id", documentId != null ? documentId : "");
            metadata.put("source_chunk_index", chunkIndex != null ? chunkIndex : 0);
            metadata.put("source_mime_type", mimeType != null ? mimeType : "text/plain");

            // 判断文件状态
            String fileStatus = "无文件";
            if ("manual_ingest".equals(source)) {
                fileStatus = "纯文本";
            } else if ("file_upload".equals(source)) {
                fileStatus = "文件正常"; // 这里假设文件存在，实际可以进一步检查
            }
            metadata.put("file_status", fileStatus);

            // 生成文件访问 URL，避免 null 值
            if (documentId != null && !documentId.isEmpty()) {
                String downloadUrl = "/api/ai/knowledge/download/" + documentId;
                String previewUrl = "/api/ai/knowledge/preview/" + documentId;
                metadata.put("download_url", downloadUrl);
                metadata.put("preview_url", previewUrl);

                // 添加调试日志确保URL格式一致
                logger.debug("RagService生成URL: documentId={}, downloadUrl={}, previewUrl={}",
                        documentId, downloadUrl, previewUrl);
            } else {
                metadata.put("download_url", "");
                metadata.put("preview_url", "");
                logger.debug("RagService: documentId为空，设置空URL");
            }

            return new Document(doc.getId(), doc.getFormattedContent(), metadata);
        }).collect(Collectors.toList());
    }

    /**
     * 为最终文档添加引用编号
     */
    private List<Document> addCitationNumbers(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());

            // 添加引用编号（从1开始）
            int citationNumber = i + 1;
            metadata.put("citation_number", citationNumber);
            metadata.put("citation_id", "ref_" + citationNumber);

            result.add(new Document(doc.getId(), doc.getFormattedContent(), metadata));
        }
        logger.info("✅ 已为 {} 个文档添加引用编号", result.size());
        return result;
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
                        String content = candidates.get(i).getFormattedContent();
                        // 清理可能导致模板解析失败的特殊字符
                        String cleanContent = content.replace("$", "\\$").replace("<", "&lt;").replace(">", "&gt;");
                        docsBuilder.append("[").append(i).append("] ").append(cleanContent).append("\n");
                    }

                    // 清理查询字符串
                    String cleanQuery = query.replace("$", "\\$").replace("<", "&lt;").replace(">", "&gt;");

                    // 加载 Prompt 模板
                    PromptTemplate promptTemplate = new PromptTemplate(rerankPromptResource);
                    String rerankPrompt = promptTemplate.render(Map.of(
                            "query", cleanQuery,
                            "documents", docsBuilder.toString(),
                            "maxIndex", candidates.size() - 1
                    ));

                    logger.debug("重排序 Prompt: {}", rerankPrompt);

                    // 使用手动 JSON 解析替代 BeanOutputConverter
                    String response = chatClient.prompt()
                            .user(rerankPrompt)
                            .options(org.springframework.ai.chat.prompt.ChatOptions.builder()
                                    .temperature(0.0)
                                    .build())
                            .call()
                            .content();

                    logger.debug("重排序 LLM 响应: {}", response);

                    // 手动解析 JSON 数组
                    List<Integer> selectedIndices = parseRerankResponse(response);

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

    /**
     * 手动解析重排序响应，避免 BeanOutputConverter 的问题
     */
    private List<Integer> parseRerankResponse(String response) {
        try {
            logger.debug("原始重排序响应: {}", response);
            
            // 清理可能的 Markdown 标记
            String cleanResponse = response.trim();
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.substring(7);
            }
            if (cleanResponse.startsWith("```")) {
                cleanResponse = cleanResponse.substring(3);
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
            }
            cleanResponse = cleanResponse.trim();

            // 检查是否包含中文提示（说明模板变量没有正确替换）
            if (cleanResponse.contains("请提供具体的用户问题") || cleanResponse.contains("候选文档列表")) {
                logger.warn("重排序响应包含模板提示，可能是变量替换失败: {}", cleanResponse);
                return List.of(0, 1, 2); // 返回默认索引
            }
            
            // 如果不是以 [ 开头，尝试提取 JSON 数组
            if (!cleanResponse.startsWith("[")) {
                int start = cleanResponse.indexOf('[');
                int end = cleanResponse.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    cleanResponse = cleanResponse.substring(start, end + 1);
                } else {
                    // 没找到 JSON 数组，返回默认值
                    logger.warn("重排序响应中未找到 JSON 数组: {}", cleanResponse);
                    return List.of(0, 1, 2);
                }
            }

            logger.debug("清理后的重排序响应: {}", cleanResponse);
            
            // 使用 ObjectMapper 解析
            List<Integer> result = objectMapper.readValue(cleanResponse, new TypeReference<List<Integer>>() {
            });

            // 验证结果
            if (result == null || result.isEmpty()) {
                logger.warn("重排序结果为空，使用默认索引");
                return List.of(0, 1, 2);
            }

            logger.debug("成功解析重排序结果: {}", result);
            return result;

        } catch (Exception e) {
            logger.error("解析重排序响应失败: {}", response, e);
            // 返回前3个索引作为降级策略
            return List.of(0, 1, 2);
        }
    }
}