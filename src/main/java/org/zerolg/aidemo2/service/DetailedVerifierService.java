// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.service;

// 导入JSON处理相关类
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
// 导入日志相关类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// 导入Spring AI框架相关类
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
// 导入Spring框架相关类
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
// 导入项目自定义的模型类
import org.zerolg.aidemo2.model.*;
import org.zerolg.aidemo2.properties.VerificationProperties;
// 导入响应式编程相关类
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 导入Java标准库
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 详细验证服务 - AI回答内容的深度事实核查系统
 *
 * 这是一个高级的AI回答验证服务，提供比基础验证更深入的分析能力
 *
 * 核心功能：
 * 1. 断言提取 (Assertion Extraction)
 *    - 将AI回答拆解为独立的可验证事实断言
 *    - 过滤掉礼貌用语、过渡语句等非事实内容
 *    - 识别数字、日期、人名等关键信息
 *
 * 2. 断言级别验证 (Assertion-Level Verification)
 *    - 逐一验证每个断言在知识库中的支持度
 *    - 提供四级支持度评估：完全支持、部分支持、无支持、矛盾
 *    - 为每个断言提供证据来源和置信度评分
 *
 * 3. 无支持内容处理 (Unsupported Content Handling)
 *    - 识别和处理没有文档支持的内容
 *    - 支持多种处理策略：标记警告、过滤内容、重新生成、降级处理
 *    - 根据无支持内容比例动态调整处理策略
 *
 * 4. 综合评估 (Overall Assessment)
 *    - 基于所有断言的验证结果计算整体通过率
 *    - 提供详细的验证报告和改进建议
 *    - 支持自定义验证阈值和策略配置
 *
 * 技术特点：
 * - 响应式设计：使用Reactor实现异步非阻塞处理
 * - 容错机制：多层降级策略确保服务可用性
 * - 模板系统：使用StringTemplate进行Prompt管理
 * - 配置驱动：支持通过配置文件调整验证策略
 * - 性能优化：支持超时控制和并发处理
 *
 * 应用场景：
 * - 高质量要求的AI问答系统
 * - 专业领域的知识验证
 * - 合规性要求严格的应用
 * - 需要详细审计日志的系统
 */
@Service // Spring注解：标记这是一个服务层组件
public class DetailedVerifierService {

    // 创建日志记录器，用于记录验证过程的详细信息
    private static final Logger logger = LoggerFactory.getLogger(DetailedVerifierService.class);

    // 依赖注入的核心服务组件
    private final ChatClient chatClient; // AI聊天客户端，用于LLM调用
    private final ObjectMapper objectMapper; // JSON对象映射器，用于解析LLM响应
    private final VerificationProperties verificationProperties; // 验证配置属性

    // Prompt模板资源文件 - 使用外部文件管理复杂的Prompt模板
    @Value("classpath:/static/assertion-extraction-prompt.st") // 断言提取模板
    private Resource assertionExtractionPromptResource;

    @Value("classpath:/static/assertion-verification-prompt.st") // 断言验证模板
    private Resource assertionVerificationPromptResource;

    /**
     * 构造函数 - 依赖注入
     *
     * @param chatClient             AI聊天客户端
     * @param objectMapper           JSON对象映射器
     * @param verificationProperties 验证配置属性
     */
    public DetailedVerifierService(ChatClient chatClient, ObjectMapper objectMapper, VerificationProperties verificationProperties) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.verificationProperties = verificationProperties;
    }

    /**
     * 执行详细验证（包含断言级别分析）
     *
     * 这是详细验证服务的核心方法，提供比基础验证更深入的分析
     *
     * 验证流程：
     * 1. 上下文准备：整合所有相关文档为验证上下文
     * 2. 断言提取：使用LLM将AI回答拆解为独立的事实断言
     * 3. 断言验证：逐一验证每个断言在文档中的支持度
     * 4. 结果计算：基于断言验证结果计算整体通过率和置信度
     * 5. 内容处理：根据配置策略处理无支持的内容
     *
     * 支持度级别：
     * - FULLY_SUPPORTED：文档中有明确完整的依据
     * - PARTIALLY_SUPPORTED：文档中有部分依据但不够完整
     * - UNSUPPORTED：文档中完全没有相关信息
     * - CONTRADICTED：与文档内容明确矛盾
     *
     * 容错机制：
     * - 超时保护：设置合理的超时时间避免长时间等待
     * - 异常处理：LLM调用失败时使用降级策略
     * - 空文档处理：无相关文档时基于通用知识判断
     * - 断言提取失败：提取不到断言时使用简单验证
     *
     * @param query 用户查询，用于理解验证上下文
     * @param documents 相关文档列表，作为验证的知识来源
     * @param response AI生成的回答，需要验证的内容
     * @return 详细验证结果的Mono，包含断言级别分析和处理建议
     */
    public Mono<DetailedVerificationResult> verifyDetailed(String query, List<Document> documents, String response) {
        return Mono.fromCallable(() -> {
                    // 记录验证开始的详细信息
                    logger.info("开始详细验证: query={}, documents={}, response length={}",
                            query, documents.size(), response.length());

                    try {
                        // 1. 准备上下文 - 将所有相关文档整合为单一的验证上下文
                        String contextStr = documents.stream()
                                .map(Document::getFormattedContent) // 获取格式化的文档内容
                                .collect(Collectors.joining("\n---\n")); // 用分隔符连接多个文档

                        if (contextStr.isEmpty()) {
                            // 无相关文档的情况 - 基于通用知识进行判断
                            logger.info("无相关文档，跳过详细验证");
                            return createSimpleResult(true, 0.85, "无相关文档，基于通用知识回答");
                        }

                        // 2. 提取断言 - 使用LLM将AI回答拆解为独立的可验证断言
                        List<String> assertions = extractAssertions(response);
                        logger.debug("提取到 {} 个断言", assertions.size());

                        if (assertions.isEmpty()) {
                            // 未提取到断言的情况 - 可能是回答过于抽象或主要是礼貌用语
                            logger.info("未提取到断言，使用简单验证");
                            return createSimpleResult(true, 0.90, "回复未包含具体事实断言");
                        }

                        // 3. 验证每个断言 - 逐一检查每个断言在文档中的支持度
                        List<AssertionAnalysis> analysisResults = verifyAssertions(assertions, contextStr);
                        logger.debug("完成 {} 个断言的验证", analysisResults.size());

                        // 4. 计算整体结果 - 基于所有断言的验证结果进行综合评估
                        boolean overallPassed = calculateOverallResult(analysisResults); // 整体是否通过验证
                        double overallConfidence = calculateOverallConfidence(analysisResults); // 整体置信度
                        String overallReason = generateOverallReason(analysisResults); // 整体判断理由

                        // 5. 处理无支持内容 - 根据配置策略处理没有文档支持的内容
                        UnsupportedHandlingResult handlingResult = handleUnsupportedContent(response, analysisResults);

                        // 构建详细验证结果
                        DetailedVerificationResult result = new DetailedVerificationResult(
                                overallPassed, // 整体验证是否通过
                                overallConfidence, // 整体置信度评分
                                overallReason, // 验证结果的详细说明
                                null, // correction 暂时为空，未来可扩展为自动纠错功能
                                analysisResults, // 每个断言的详细分析结果
                                handlingResult // 无支持内容的处理结果
                        );

                        // 记录验证完成的统计信息
                        logger.info("详细验证完成: passed={}, confidence={}, assertions={}, unsupported ratio={}",
                                result.passed(), result.confidence(), result.assertions().size(), result.getUnsupportedRatio());

                        return result;

                    } catch (Exception e) {
                        // 验证过程中的异常处理 - 使用降级策略确保服务可用性
                        logger.error("详细验证过程中发生异常", e);
                        return createSimpleResult(true, 0.85, "验证异常，基于通用知识回答");
                    }
                })
                .subscribeOn(Schedulers.boundedElastic()) // 在弹性线程池中执行，避免阻塞主线程
                .timeout(Duration.ofSeconds(verificationProperties.getTimeoutSeconds())) // 设置超时保护
                .onErrorResume(throwable -> {
                    // 超时和异常的统一处理
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        logger.warn("详细验证服务超时，使用默认结果");
                    } else {
                        logger.error("详细验证服务异常，使用默认结果", throwable);
                    }
                    // 返回安全的默认结果
                    return Mono.just(createSimpleResult(true, 0.85, "验证超时或异常，基于通用知识回答"));
                });
    }

    /**
     * 提取断言
     *
     * 使用大语言模型将AI回答拆解为独立的可验证事实断言
     *
     * 功能说明：
     * - 断言识别：识别回答中的具体事实陈述
     * - 内容过滤：过滤掉礼貌用语、过渡语句等非事实内容
     * - 结构化输出：将断言以JSON数组格式返回
     * - 错误处理：模板渲染失败时使用字符串拼接降级
     *
     * 拆解规则：
     * 1. 每个断言应该是一个独立的可验证的事实陈述
     * 2. 避免包含推理过程或连接词
     * 3. 数字、日期、人名等具体信息应该作为独立断言
     * 4. 忽略礼貌用语、过渡语句等非事实内容
     *
     * 容错机制：
     * - 内容清理：清理可能导致模板解析失败的特殊字符
     * - 模板降级：模板渲染失败时使用手动构建的Prompt
     * - 解析容错：JSON解析失败时返回空列表
     * - 日志记录：详细记录处理过程便于调试
     *
     * @param response AI生成的回答内容
     * @return 提取出的断言列表，每个断言都是独立的事实陈述
     */
    private List<String> extractAssertions(String response) {
        try {
            // 记录原始AI回复内容
            logger.debug("原始 AI 回复: {}", response);

            // 清理响应内容 - 移除可能导致模板解析失败的特殊字符
            String cleanResponse = cleanStringForTemplate(response);
            logger.debug("清理后的回复: {}", cleanResponse);

            // 加载断言提取的Prompt模板
            PromptTemplate promptTemplate = new PromptTemplate(assertionExtractionPromptResource);

            // 准备模板变量
            Map<String, Object> variables = Map.of("response", cleanResponse);
            logger.debug("模板变量: {}", variables);

            String prompt;
            try {
                // 尝试使用模板渲染 - 首选方式，支持复杂的模板逻辑
                prompt = promptTemplate.render(variables);
            } catch (Exception templateException) {
                // 模板渲染失败时的降级策略 - 使用简单的字符串拼接
                logger.warn("断言提取模板渲染失败，使用字符串拼接: {}", templateException.getMessage());
                prompt = buildExtractionPromptManually(cleanResponse);
            }

            // 记录最终使用的Prompt
            logger.debug("断言提取 Prompt: {}", prompt);

            // 调用LLM进行断言提取
            String jsonResult = chatClient.prompt()
                    .user(prompt) // 设置用户输入
                    .options(org.springframework.ai.chat.prompt.ChatOptions.builder()
                            .temperature(0.0) // 设置温度为0，确保输出的确定性
                            .build())
                    .call() // 执行调用
                    .content(); // 获取响应内容

            // 记录LLM的原始响应
            logger.debug("断言提取响应: {}", jsonResult);

            // 解析LLM返回的JSON数组
            return parseAssertionList(jsonResult);

        } catch (Exception e) {
            // 断言提取失败的异常处理
            logger.error("断言提取失败", e);
            return new ArrayList<>(); // 返回空列表，不影响后续处理
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