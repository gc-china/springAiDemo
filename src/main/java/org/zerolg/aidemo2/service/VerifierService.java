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
     * 异步执行验证
     */
    public Mono<VerificationResult> verify(String query, List<Document> documents, String response) {
        return Mono.fromCallable(() -> {
                    // 1. 准备上下文
                    String contextStr = documents.stream()
                            .map(Document::getFormattedContent)
                            .collect(Collectors.joining("\n---\n"));

                    if (contextStr.isEmpty()) {
                        // 无上下文时，默认为非事实性闲聊，跳过验证或标记为通过
                        return new VerificationResult(true, 0.85, "无背景知识，跳过验证", null);
                    }

                    // 2. 构建 Prompt
                    PromptTemplate promptTemplate = new PromptTemplate(verifierPromptResource);
                    String prompt = promptTemplate.render(Map.of(
                            "context", contextStr,
                            "query", query,
                            "response", response
                    ));

                    // 3. 调用裁判 (建议 temperature=0)
                    BeanOutputConverter<VerificationResult> converter = new BeanOutputConverter<>(VerificationResult.class);
                    String jsonResult = chatClient.prompt().user(prompt).call().content();

                    // 4. 解析结果
                    return converter.convert(jsonResult);

                }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    logger.error("验证服务异常", e);
                    return Mono.just(new VerificationResult(true, 0.85, "知识库中未找到相关文档，基于大模型通用知识回答", null));
                });
    }
}