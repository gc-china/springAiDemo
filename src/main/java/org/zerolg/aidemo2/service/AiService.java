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
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final String[] availableTools; // 所有可用工具

    @Value("classpath:/static/rag-enhanced-prompt.st")
    private Resource ragEnhancedPromptResource;
    
    // RAG 检索配置参数
    @Value("${ai.rag.topK:8}")
    private int ragTopK;
    
    @Value("${ai.rag.similarityThreshold:0.4}")
    private double ragSimilarityThreshold;

    public AiService(ChatClient chatClient, VectorStore vectorStore, List<String> availableToolNames) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.availableTools = availableToolNames.toArray(new String[0]);
        logger.info("AiService 初始化完成,加载了 {} 个工具: {}", availableTools.length, String.join(", ", availableTools));
    }

    /**
     * 处理用户查询
     * 
     * 流程:
     * 1. RAG 检索 - 从向量数据库检索相关文档
     * 2. 重排序 - 使用 LLM 对文档进行精选
     * 3. 构建 Prompt - 将上下文注入到 System Message
     * 4. 调用 AI - 使用工具增强的对话
     * 
     * @param msg 用户查询内容
     * @return 流式响应
     */
    public Flux<String> processQuery(String msg) {
        logger.debug("开始处理查询: {}", msg);
        
        // 1. RAG 检索 (Retrieve) - 扩大范围 (Recall)
        // 策略：TopK 设大，阈值设低，先捞上来再说
        SearchRequest searchRequest = SearchRequest.builder()
                .query(msg)
                .topK(ragTopK) 
                .similarityThreshold(ragSimilarityThreshold) 
                .build();

        List<Document> initialDocuments = vectorStore.similaritySearch(searchRequest);
        
        // 2. 重排序 (Re-ranking) - 专家评审
        // 使用 LLM 对初筛结果进行精选,只保留真正相关的 Top 3
        List<Document> finalDocuments = rerankDocuments(msg, initialDocuments);

        String context = finalDocuments.stream()
                .map(Document::getFormattedContent)
                .collect(Collectors.joining("\n\n"));
        
        // 3. 构建 Prompt
        // 从 .st 模板文件加载 System Prompt，并注入 RAG 检索到的上下文
        // 这样 AI 就能基于知识库内容回答问题，而不是仅凭训练数据
        PromptTemplate systemPromptTemplate = new PromptTemplate(ragEnhancedPromptResource);
        String systemText = systemPromptTemplate.render(Map.of(
                "context", context.isEmpty() ? "无" : context
        ));
        
        logger.debug("使用 {} 个工具进行查询", availableTools.length);

        return chatClient.prompt()
                .system(systemText) // 使用 System Role 注入规则和上下文
                .user(msg)          // User Role 只放问题
                .toolNames(availableTools) // 使用自动加载的所有工具
                .stream()
                .content();
    }

    /**
     * 使用 LLM 进行重排序 (Listwise Reranking)
     */
    private List<Document> rerankDocuments(String query, List<Document> documents) {
        if (documents.isEmpty()) {
            return new ArrayList<>();
        }

        // 构建重排序 Prompt
        StringBuilder docsBuilder = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            docsBuilder.append("[").append(i).append("] ").append(documents.get(i).getFormattedContent()).append("\n");
        }

        String rerankPrompt = """
                你是一个专业的文档相关性评审专家。
                
                【用户问题】：%s
                
                【候选文档列表】：
                %s
                
                请分析上述文档与用户问题的相关性。
                请选出最相关的最多 3 个文档的编号（0-%d）。
                如果都不相关,请返回空列表。
                
                请仅返回纯数字编号的 JSON 数组,例如：[0, 2, 1]。不要包含任何其他解释或 Markdown 标记。
                """.formatted(query, docsBuilder.toString(), documents.size() - 1);

        try {
            // 调用 LLM 获取评审结果
            String response = chatClient.prompt()
                    .user(rerankPrompt)
                    .call()
                    .content();
            
            // 简单的解析逻辑 (生产环境建议用 BeanOutputConverter)
            // 清理可能存在的 markdown 标记 ```json ... ```
            String cleanJson = response.replaceAll("```json", "").replaceAll("```", "").trim();
            
            // 提取数字
            List<Integer> selectedIndices = new ArrayList<>();
            if (cleanJson.startsWith("[") && cleanJson.endsWith("]")) {
                String[] parts = cleanJson.substring(1, cleanJson.length() - 1).split(",");
                for (String part : parts) {
                    if (!part.trim().isEmpty()) {
                        try {
                            selectedIndices.add(Integer.parseInt(part.trim()));
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            }

            // 根据索引构建最终列表
            List<Document> rerankedDocs = new ArrayList<>();
            for (Integer index : selectedIndices) {
                if (index >= 0 && index < documents.size()) {
                    rerankedDocs.add(documents.get(index));
                }
            }
            return rerankedDocs;

        } catch (Exception e) {
            // 如果重排序失败（如 LLM 响应超时或格式错误）,降级为直接返回前 3 个原文档
            logger.warn("重排序失败，降级使用前3个文档: {}", e.getMessage());
            return documents.stream().limit(3).collect(Collectors.toList());
        }
    }
}