package org.zerolg.aidemo2.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.model.*;
import org.zerolg.aidemo2.properties.VerificationProperties;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 详细验证服务 - 支持断言级别分析和无支持内容处理
 */
@Service
public class DetailedVerifierService {

    private static final Logger logger = LoggerFactory.getLogger(DetailedVerifierService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final VerificationProperties verificationProperties;

    @Value("classpath:/static/assertion-extraction-prompt.st")
    private Resource assertionExtractionPromptResource;

    @Value("classpath:/static/assertion-verification-prompt.st")
    private Resource assertionVerificationPromptResource;

    public DetailedVerifierService(ChatClient chatClient, ObjectMapper objectMapper, VerificationProperties verificationProperties) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.verificationProperties = verificationProperties;
    }

    /**
     * 执行详细验证（包含断言级别分析）
     */
    public Mono<DetailedVerificationResult> verifyDetailed(String query, List<Document> documents, String response) {
        return Mono.fromCallable(() -> {
                    logger.info("开始详细验证: query={}, documents={}, response length={}",
                            query, documents.size(), response.length());

                    try {
                        // 1. 准备上下文
                        String contextStr = documents.stream()
                                .map(Document::getFormattedContent)
                                .collect(Collectors.joining("\n---\n"));

                        if (contextStr.isEmpty()) {
                            logger.info("无相关文档，跳过详细验证");
                            return createSimpleResult(true, 0.85, "无相关文档，基于通用知识回答");
                        }

                        // 2. 提取断言
                        List<String> assertions = extractAssertions(response);
                        logger.debug("提取到 {} 个断言", assertions.size());

                        if (assertions.isEmpty()) {
                            logger.info("未提取到断言，使用简单验证");
                            return createSimpleResult(true, 0.90, "回复未包含具体事实断言");
                        }

                        // 3. 验证每个断言
                        List<AssertionAnalysis> analysisResults = verifyAssertions(assertions, contextStr);
                        logger.debug("完成 {} 个断言的验证", analysisResults.size());

                        // 4. 计算整体结果
                        boolean overallPassed = calculateOverallResult(analysisResults);
                        double overallConfidence = calculateOverallConfidence(analysisResults);
                        String overallReason = generateOverallReason(analysisResults);

                        // 5. 处理无支持内容
                        UnsupportedHandlingResult handlingResult = handleUnsupportedContent(response, analysisResults);

                        DetailedVerificationResult result = new DetailedVerificationResult(
                                overallPassed,
                                overallConfidence,
                                overallReason,
                                null, // correction 暂时为空
                                analysisResults,
                                handlingResult
                        );

                        logger.info("详细验证完成: passed={}, confidence={}, assertions={}, unsupported ratio={}",
                                result.passed(), result.confidence(), result.assertions().size(), result.getUnsupportedRatio());

                        return result;

                    } catch (Exception e) {
                        logger.error("详细验证过程中发生异常", e);
                        return createSimpleResult(true, 0.85, "验证异常，基于通用知识回答");
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(verificationProperties.getTimeoutSeconds()))
                .onErrorResume(throwable -> {
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        logger.warn("详细验证服务超时，使用默认结果");
                    } else {
                        logger.error("详细验证服务异常，使用默认结果", throwable);
                    }
                    return Mono.just(createSimpleResult(true, 0.85, "验证超时或异常，基于通用知识回答"));
                });
    }

    /**
     * 提取断言
     */
    private List<String> extractAssertions(String response) {
        try {
            logger.debug("原始 AI 回复: {}", response);

            // 清理响应内容
            String cleanResponse = cleanStringForTemplate(response);
            logger.debug("清理后的回复: {}", cleanResponse);

            PromptTemplate promptTemplate = new PromptTemplate(assertionExtractionPromptResource);

            Map<String, Object> variables = Map.of("response", cleanResponse);
            logger.debug("模板变量: {}", variables);

            String prompt;
            try {
                // 尝试使用模板渲染
                prompt = promptTemplate.render(variables);
            } catch (Exception templateException) {
                logger.warn("断言提取模板渲染失败，使用字符串拼接: {}", templateException.getMessage());
                // 降级到字符串拼接
                prompt = buildExtractionPromptManually(cleanResponse);
            }

            logger.debug("断言提取 Prompt: {}", prompt);

            String jsonResult = chatClient.prompt()
                    .user(prompt)
                    .options(org.springframework.ai.chat.prompt.ChatOptions.builder()
                            .temperature(0.0)
                            .build())
                    .call()
                    .content();

            logger.debug("断言提取响应: {}", jsonResult);

            return parseAssertionList(jsonResult);

        } catch (Exception e) {
            logger.error("断言提取失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 验证断言列表
     */
    private List<AssertionAnalysis> verifyAssertions(List<String> assertions, String context) {
        try {
            // 清理上下文和断言，处理更多特殊字符
            String cleanContext = cleanStringForTemplate(context);
            String assertionsStr = String.join("\n", assertions.stream()
                    .map(a -> "- " + cleanStringForTemplate(a))
                    .collect(Collectors.toList()));

            String prompt;
            try {
                // 尝试使用模板渲染
                PromptTemplate promptTemplate = new PromptTemplate(assertionVerificationPromptResource);
                prompt = promptTemplate.render(Map.of(
                        "context", cleanContext,
                        "assertions", assertionsStr
                ));
            } catch (Exception templateException) {
                logger.warn("模板渲染失败，使用字符串拼接: {}", templateException.getMessage());
                // 降级到字符串拼接
                prompt = buildVerificationPromptManually(cleanContext, assertionsStr);
            }

            logger.debug("断言验证 Prompt: {}", prompt);

            String jsonResult = chatClient.prompt()
                    .user(prompt)
                    .options(org.springframework.ai.chat.prompt.ChatOptions.builder()
                            .temperature(0.0)
                            .build())
                    .call()
                    .content();

            logger.debug("断言验证响应: {}", jsonResult);

            return parseAssertionAnalysisList(jsonResult);

        } catch (Exception e) {
            logger.error("断言验证失败", e);
            // 降级处理：将所有断言标记为无支持
            return assertions.stream()
                    .map(assertion -> AssertionAnalysis.unsupported(assertion, 0.5))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 解析断言列表
     */
    private List<String> parseAssertionList(String jsonResult) {
        try {
            String cleanJson = cleanJsonResponse(jsonResult);
            return objectMapper.readValue(cleanJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            logger.error("解析断言列表失败: {}", jsonResult, e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析断言分析列表
     */
    private List<AssertionAnalysis> parseAssertionAnalysisList(String jsonResult) {
        try {
            String cleanJson = cleanJsonResponse(jsonResult);
            List<Map<String, Object>> rawList = objectMapper.readValue(cleanJson, new TypeReference<List<Map<String, Object>>>() {
            });

            return rawList.stream().map(this::mapToAssertionAnalysis).collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("解析断言分析列表失败: {}", jsonResult, e);
            return new ArrayList<>();
        }
    }

    /**
     * 将 Map 转换为 AssertionAnalysis
     */
    private AssertionAnalysis mapToAssertionAnalysis(Map<String, Object> map) {
        String assertion = (String) map.get("assertion");
        String supportLevelStr = (String) map.get("supportLevel");
        String evidence = (String) map.get("evidence");
        String sourceLocation = (String) map.get("sourceLocation");
        double confidence = ((Number) map.getOrDefault("confidence", 0.5)).doubleValue();

        AssertionSupportLevel supportLevel;
        try {
            supportLevel = AssertionSupportLevel.valueOf(supportLevelStr);
        } catch (Exception e) {
            logger.warn("无效的支持度级别: {}, 使用默认值 UNSUPPORTED", supportLevelStr);
            supportLevel = AssertionSupportLevel.UNSUPPORTED;
        }

        return new AssertionAnalysis(assertion, supportLevel, evidence, sourceLocation, confidence);
    }

    /**
     * 清理 JSON 响应
     */
    private String cleanJsonResponse(String jsonResult) {
        String cleanJson = jsonResult.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7);
        }
        if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3);
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        }
        return cleanJson.trim();
    }

    /**
     * 计算整体验证结果
     */
    private boolean calculateOverallResult(List<AssertionAnalysis> analyses) {
        if (analyses.isEmpty()) return true;

        long supportedCount = analyses.stream()
                .mapToLong(a -> (a.supportLevel() == AssertionSupportLevel.FULLY_SUPPORTED ||
                        a.supportLevel() == AssertionSupportLevel.PARTIALLY_SUPPORTED) ? 1 : 0)
                .sum();

        // 如果超过一半的断言有支持，则认为整体通过
        return supportedCount >= analyses.size() / 2.0;
    }

    /**
     * 计算整体置信度
     */
    private double calculateOverallConfidence(List<AssertionAnalysis> analyses) {
        if (analyses.isEmpty()) return 0.85;

        return analyses.stream()
                .mapToDouble(AssertionAnalysis::confidence)
                .average()
                .orElse(0.85);
    }

    /**
     * 生成整体判断理由
     */
    private String generateOverallReason(List<AssertionAnalysis> analyses) {
        if (analyses.isEmpty()) {
            return "未提取到具体断言";
        }

        long supportedCount = analyses.stream()
                .mapToLong(a -> (a.supportLevel() == AssertionSupportLevel.FULLY_SUPPORTED ||
                        a.supportLevel() == AssertionSupportLevel.PARTIALLY_SUPPORTED) ? 1 : 0)
                .sum();

        long unsupportedCount = analyses.stream()
                .mapToLong(a -> a.supportLevel() == AssertionSupportLevel.UNSUPPORTED ? 1 : 0)
                .sum();

        return String.format("共分析 %d 个断言，其中 %d 个有支持依据，%d 个无支持依据",
                analyses.size(), supportedCount, unsupportedCount);
    }

    /**
     * 处理无支持内容
     */
    private UnsupportedHandlingResult handleUnsupportedContent(String originalContent, List<AssertionAnalysis> analyses) {
        if (analyses.isEmpty()) {
            return UnsupportedHandlingResult.downgrade(originalContent);
        }

        double unsupportedRatio = (double) analyses.stream()
                .mapToLong(a -> a.supportLevel() == AssertionSupportLevel.UNSUPPORTED ? 1 : 0)
                .sum() / analyses.size();

        VerificationProperties.UnsupportedHandling config = verificationProperties.getUnsupportedHandling();

        if (unsupportedRatio <= config.getThreshold()) {
            // 无支持内容比例在可接受范围内
            return UnsupportedHandlingResult.markWarning(originalContent,
                    String.format("%.1f%% 的内容无文档支持", unsupportedRatio * 100));
        }

        // 根据配置的策略处理
        return switch (config.getStrategy()) {
            case MARK_WARNING -> UnsupportedHandlingResult.markWarning(originalContent,
                    String.format("%.1f%% 的内容无文档支持，请谨慎参考", unsupportedRatio * 100));
            case FILTER_CONTENT -> filterUnsupportedContent(originalContent, analyses);
            case REGENERATE -> UnsupportedHandlingResult.triggerRegeneration(originalContent);
            case DOWNGRADE -> UnsupportedHandlingResult.downgrade(originalContent);
        };
    }

    /**
     * 过滤无支持内容
     */
    private UnsupportedHandlingResult filterUnsupportedContent(String originalContent, List<AssertionAnalysis> analyses) {
        // 简化实现：这里可以根据断言分析结果重新组织内容
        // 目前先返回标记警告的结果
        return UnsupportedHandlingResult.markWarning(originalContent, "包含无支持依据的内容");
    }

    /**
     * 创建简单验证结果
     */
    private DetailedVerificationResult createSimpleResult(boolean passed, double confidence, String reason) {
        return new DetailedVerificationResult(
                passed,
                confidence,
                reason,
                null,
                new ArrayList<>(),
                UnsupportedHandlingResult.downgrade("无详细分析")
        );
    }

    /**
     * 清理字符串以避免 StringTemplate 解析错误
     * 采用最保守的清理策略，只处理绝对必要的字符
     */
    private String cleanStringForTemplate(String input) {
        if (input == null) return "";

        return input
                // 只处理最基本的字符，避免过度清理
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                // 移除可能导致解析问题的控制字符
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                // 移除可能导致 StringTemplate 解析问题的字符
                .replace("【", "[")
                .replace("】", "]")
                .replace("'", "'")
                .replace("'", "'");
    }

    /**
     * 手动构建验证 Prompt（当模板渲染失败时使用）
     */
    private String buildVerificationPromptManually(String context, String assertions) {
        return "你是一个严格的事实核查专家，负责验证每个断言的支持度。\n\n" +
                "背景文档：\n" + context + "\n\n" +
                "待验证断言列表：\n" + assertions + "\n\n" +
                "请逐一分析每个断言在背景文档中的支持情况。\n\n" +
                "支持度级别：\n" +
                "- FULLY_SUPPORTED: 文档中有明确完整的依据\n" +
                "- PARTIALLY_SUPPORTED: 文档中有部分依据但不够完整\n" +
                "- UNSUPPORTED: 文档中完全没有相关信息\n" +
                "- CONTRADICTED: 与文档内容明确矛盾\n\n" +
                "请返回JSON格式结果，示例：\n" +
                "[{\"assertion\": \"Product price is 100 yuan\", \"supportLevel\": \"FULLY_SUPPORTED\", \"evidence\": \"Document clearly states price is 100 yuan\", \"sourceLocation\": \"Paragraph 1\", \"confidence\": 0.95}]\n\n" +
                "只返回JSON数组，不要其他内容。";
    }

    /**
     * 手动构建提取 Prompt（当模板渲染失败时使用）
     */
    private String buildExtractionPromptManually(String response) {
        return "你是一个专业的文本分析专家，负责将AI回复拆解为独立的事实断言。\n\n" +
                "AI回复：\n" + response + "\n\n" +
                "请将上述回复拆解为独立的事实断言。\n\n" +
                "拆解规则：\n" +
                "1. 每个断言应该是一个独立的可验证的事实陈述\n" +
                "2. 避免包含推理过程或连接词\n" +
                "3. 数字日期人名等具体信息应该作为独立断言\n" +
                "4. 忽略礼貌用语过渡语句等非事实内容\n\n" +
                "请仅返回JSON数组格式：[\"assertion1\", \"assertion2\", \"assertion3\"]\n\n" +
                "只返回JSON数组，不要其他内容。";
    }

}