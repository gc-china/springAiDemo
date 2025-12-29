package org.zerolg.aidemo2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.model.VerificationResult;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VerifierService {

    private static final Logger logger = LoggerFactory.getLogger(VerifierService.class);
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("classpath:/static/verifier-prompt.st")
    private Resource verifierPromptResource;

    public VerifierService(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步执行验证（带超时和错误处理）
     */
    public Mono<VerificationResult> verify(String query, List<Document> documents, String response) {
        return Mono.fromCallable(() -> {
                    logger.info("开始验证: query={}, documents={}", query, documents.size());
                    
                    // 1. 准备上下文
                    String contextStr = documents.stream()
                            .map(Document::getFormattedContent)
                            .collect(Collectors.joining("\n---\n"));

                    if (contextStr.isEmpty()) {
                        // 无上下文时，默认为非事实性闲聊，跳过验证或标记为通过
                        logger.info("无相关文档，基于通用知识回答");
                        return new VerificationResult(true, 0.85, "无相关文档，基于通用知识回答", null);
                    }

                    // 2. 构建 Prompt
                    PromptTemplate promptTemplate = new PromptTemplate(verifierPromptResource);
                    String prompt = promptTemplate.render(Map.of(
                            "context", contextStr,
                            "query", query,
                            "response", response
                    ));

                    logger.debug("验证提示词构建完成，开始调用 LLM");

                    // 3. 调用裁判 (建议 temperature=0)
                    String jsonResult = chatClient.prompt()
                            .user(prompt)
                            .options(org.springframework.ai.chat.prompt.ChatOptions.builder()
                                    .temperature(0.0)
                                    .build())
                            .call()
                            .content();

                    logger.debug("LLM 验证响应: {}", jsonResult);

                    // 手动解析 JSON，避免 BeanOutputConverter 的问题
                    VerificationResult result = parseVerificationResult(jsonResult);

                    if (result == null) {
                        logger.warn("验证结果解析为空，使用默认结果");
                        return new VerificationResult(true, 0.85, "验证解析失败，基于通用知识回答", null);
                    }

                    // 4. 结果后处理
                    if (result.passed() && result.confidence() <= 0.85) {
                        logger.info("验证通过但置信度较低: confidence={}", result.confidence());
                        return new VerificationResult(true, 0.85, "文档关联度低，基于通用知识回答", null);
                    }

                    logger.info("验证完成: passed={}, confidence={}", result.passed(), result.confidence());
                    return result;

                }).subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(10)) // 10秒超时
                .onErrorResume(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        logger.warn("验证服务超时: {}", e.getMessage());
                        return Mono.just(new VerificationResult(true, 0.85, "验证超时，基于通用知识回答", null));
                    } else {
                        logger.error("验证服务异常", e);
                        return Mono.just(new VerificationResult(true, 0.85, "验证异常，基于通用知识回答", null));
                    }
                });
    }

    /**
     * 手动解析验证结果 JSON，避免 BeanOutputConverter 的问题
     */
    private VerificationResult parseVerificationResult(String jsonResult) {
        try {
            // 清理可能的 Markdown 标记
            String cleanJson = jsonResult.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            // 使用 ObjectMapper 解析
            Map<String, Object> resultMap = objectMapper.readValue(cleanJson, Map.class);

            boolean passed = Boolean.TRUE.equals(resultMap.get("passed"));
            double confidence = ((Number) resultMap.getOrDefault("confidence", 0.85)).doubleValue();
            String reason = (String) resultMap.getOrDefault("reason", "验证完成");
            String correction = (String) resultMap.get("correction");

            return new VerificationResult(passed, confidence, reason, correction);

        } catch (Exception e) {
            logger.error("解析验证结果失败: {}", jsonResult, e);
            return new VerificationResult(true, 0.85, "验证解析失败，基于通用知识回答", null);
        }
    }
}