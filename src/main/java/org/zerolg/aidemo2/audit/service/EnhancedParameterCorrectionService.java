package org.zerolg.aidemo2.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.audit.model.ParameterChain;
import org.zerolg.aidemo2.audit.model.ParameterTransformation;
import org.zerolg.aidemo2.correction.*;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.CorrectionStatus;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强的参数修正服务，集成审计功能
 * 直接使用现有的参数清洗层组件，并记录详细的转换链
 */
@Service
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = false)
public class EnhancedParameterCorrectionService {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedParameterCorrectionService.class);

    @Autowired
    private ParameterCorrectionService originalService;

    @Autowired
    private ParameterChainRecorder parameterChainRecorder;

    // 直接注入现有的参数清洗组件
    @Autowired
    private List<ParamNormalizer> normalizers;

    @Autowired
    private List<EntityResolver> entityResolvers;

    @Autowired
    private List<ParameterValidator> validators;

    @Autowired
    private List<AmbiguityHandler> ambiguityHandlers;

    /**
     * 修正参数并记录详细转换链（使用现有清洗层）
     */
    public CorrectionResult correctParameterWithDetailedAudit(String parameterName, Class<?> parameterType,
                                                              Object originalValue, Parameter parameter,
                                                              String methodName, String executionId) {

        // 创建参数链
        Map<String, Object> originalParams = Map.of(parameterName, originalValue);
        ParameterChain chain = ParameterChain.create(executionId, originalParams);

        try {
            // 创建参数上下文
            ParameterContext context = ParameterContext.create(
                    parameterName, parameterType, originalValue, parameter, methodName
            );

            logger.debug("开始增强参数修正: name={}, type={}, value={}",
                    parameterName, parameterType.getSimpleName(), originalValue);

            // 阶段1: 标准化 - 直接使用现有组件并记录
            CorrectionResult normalizationResult = applyNormalizationWithAudit(context, chain, executionId);
            if (normalizationResult.status() == CorrectionStatus.FAILED) {
                parameterChainRecorder.recordParameterChain(executionId, chain);
                return normalizationResult;
            }

            // 更新上下文
            context = updateContext(context, normalizationResult);

            // 阶段2: 实体解析 - 直接使用现有组件并记录
            CorrectionResult resolutionResult = applyEntityResolutionWithAudit(context, chain, executionId);
            if (resolutionResult.status() == CorrectionStatus.FAILED) {
                parameterChainRecorder.recordParameterChain(executionId, chain);
                return combineResults(normalizationResult, resolutionResult);
            }

            // 处理多候选情况
            if (resolutionResult.status() == CorrectionStatus.NEEDS_CONFIRMATION) {
                @SuppressWarnings("unchecked")
                List<Object> candidates = (List<Object>) resolutionResult.metadata().get("candidates");
                CorrectionResult ambiguityResult = handleAmbiguityWithAudit(context, candidates, chain, executionId);
                parameterChainRecorder.recordParameterChain(executionId, chain);
                return combineResults(normalizationResult, ambiguityResult);
            }

            // 更新上下文
            context = updateContext(context, resolutionResult);

            // 阶段3: 验证 - 直接使用现有组件并记录
            CorrectionResult validationResult = applyValidationWithAudit(context, chain, executionId);

            // 记录完整的参数链
            parameterChainRecorder.recordParameterChain(executionId, chain);

            // 合并所有结果
            CorrectionResult finalResult = combineResults(normalizationResult, resolutionResult, validationResult);

            logger.debug("增强参数修正完成: {} -> {}, 转换步骤: {}",
                    originalValue, finalResult.correctedValue(), chain.steps().size());

            return finalResult;

        } catch (Exception e) {
            logger.error("增强参数修正失败", e);

            // 记录失败的转换
            ParameterTransformation failedTransformation = ParameterTransformation.create(
                    parameterName, originalValue, originalValue, "error", 0.0,
                    "修正失败: " + e.getMessage()
            ).withMetadata(Map.of(
                    "error", e.getMessage(),
                    "timestamp", Instant.now().toString(),
                    "phase", "unknown"
            ));

            chain = chain.addTransformation(failedTransformation);
            parameterChainRecorder.recordParameterChain(executionId, chain);

            return CorrectionResult.failed(originalValue.toString(), "参数修正异常: " + e.getMessage());
        }
    }

    /**
     * 应用标准化并记录转换
     */
    private CorrectionResult applyNormalizationWithAudit(ParameterContext context, ParameterChain chain, String executionId) {
        for (ParamNormalizer normalizer : normalizers) {
            if (normalizer.supports(context)) {
                CorrectionResult result = normalizer.normalize(context);
                if (result.status() != CorrectionStatus.NO_CORRECTION_NEEDED) {

                    // 记录标准化转换
                    ParameterTransformation transformation = ParameterTransformation.create(
                            context.parameterName(),
                            context.originalValue(),
                            result.correctedValue(),
                            "normalization",
                            result.confidence(),
                            String.join("; ", result.corrections())
                    ).withMetadata(Map.of(
                            "normalizer", normalizer.getClass().getSimpleName(),
                            "priority", normalizer.getPriority(),
                            "timestamp", Instant.now().toString(),
                            "phase", "normalization",
                            "status", result.status().toString()
                    ));

                    // 更新参数链
                    ParameterChain updatedChain = chain.addTransformation(transformation);

                    logger.debug("标准化应用: normalizer={}, {} -> {}",
                            normalizer.getClass().getSimpleName(),
                            context.originalValue(), result.correctedValue());

                    return result;
                }
            }
        }

        return CorrectionResult.noCorrection(context.originalValue());
    }

    /**
     * 应用实体解析并记录转换
     */
    private CorrectionResult applyEntityResolutionWithAudit(ParameterContext context, ParameterChain chain, String executionId) {
        for (EntityResolver resolver : entityResolvers) {
            if (resolver.supports(context.parameterType())) {
                CorrectionResult result = resolver.resolve(context);
                if (result.status() != CorrectionStatus.NO_CORRECTION_NEEDED) {

                    // 记录实体解析转换
                    ParameterTransformation transformation = ParameterTransformation.create(
                            context.parameterName(),
                            context.originalValue(),
                            result.correctedValue(),
                            "entity_resolution",
                            result.confidence(),
                            String.join("; ", result.corrections())
                    ).withMetadata(Map.of(
                            "resolver", resolver.getClass().getSimpleName(),
                            "priority", resolver.getPriority(),
                            "timestamp", Instant.now().toString(),
                            "phase", "entity_resolution",
                            "status", result.status().toString(),
                            "candidates_count", result.metadata().getOrDefault("candidates", Collections.emptyList()).toString()
                    ));

                    // 更新参数链
                    ParameterChain updatedChain = chain.addTransformation(transformation);

                    logger.debug("实体解析应用: resolver={}, {} -> {}",
                            resolver.getClass().getSimpleName(),
                            context.originalValue(), result.correctedValue());

                    return result;
                }
            }
        }

        return CorrectionResult.noCorrection(context.originalValue());
    }

    /**
     * 应用验证并记录转换
     */
    private CorrectionResult applyValidationWithAudit(ParameterContext context, ParameterChain chain, String executionId) {
        for (ParameterValidator validator : validators) {
            if (validator.supports(context)) {
                CorrectionResult result = validator.validate(context);
                if (result.status() != CorrectionStatus.NO_CORRECTION_NEEDED) {

                    // 记录验证转换
                    ParameterTransformation transformation = ParameterTransformation.create(
                            context.parameterName(),
                            context.originalValue(),
                            result.correctedValue(),
                            "validation",
                            result.confidence(),
                            String.join("; ", result.corrections())
                    ).withMetadata(Map.of(
                            "validator", validator.getClass().getSimpleName(),
                            "priority", validator.getPriority(),
                            "timestamp", Instant.now().toString(),
                            "phase", "validation",
                            "status", result.status().toString()
                    ));

                    // 更新参数链
                    ParameterChain updatedChain = chain.addTransformation(transformation);

                    logger.debug("验证应用: validator={}, {} -> {}",
                            validator.getClass().getSimpleName(),
                            context.originalValue(), result.correctedValue());

                    return result;
                }
            }
        }

        return CorrectionResult.noCorrection(context.originalValue());
    }

    /**
     * 处理歧义并记录转换
     */
    private CorrectionResult handleAmbiguityWithAudit(ParameterContext context, List<Object> candidates,
                                                      ParameterChain chain, String executionId) {
        for (AmbiguityHandler handler : ambiguityHandlers) {
            if (handler.supports(context)) {
                CorrectionResult result = handler.handleAmbiguity(context, candidates);

                // 记录歧义处理转换
                ParameterTransformation transformation = ParameterTransformation.create(
                        context.parameterName(),
                        context.originalValue(),
                        result.correctedValue(),
                        "ambiguity_resolution",
                        result.confidence(),
                        String.join("; ", result.corrections())
                ).withMetadata(Map.of(
                        "handler", handler.getClass().getSimpleName(),
                        "priority", handler.getPriority(),
                        "timestamp", Instant.now().toString(),
                        "phase", "ambiguity_resolution",
                        "status", result.status().toString(),
                        "candidates_count", candidates.size(),
                        "candidates", candidates.stream().map(Object::toString).collect(Collectors.joining(", "))
                ));

                // 更新参数链
                ParameterChain updatedChain = chain.addTransformation(transformation);

                logger.debug("歧义处理应用: handler={}, candidates={}",
                        handler.getClass().getSimpleName(), candidates.size());

                return result;
            }
        }

        // 默认处理
        ParameterTransformation defaultTransformation = ParameterTransformation.create(
                context.parameterName(),
                context.originalValue(),
                candidates.get(0),
                "default_ambiguity_resolution",
                0.5,
                "使用默认歧义处理"
        ).withMetadata(Map.of(
                "handler", "default",
                "timestamp", Instant.now().toString(),
                "phase", "ambiguity_resolution",
                "candidates_count", candidates.size()
        ));

        ParameterChain updatedChain = chain.addTransformation(defaultTransformation);

        return CorrectionResult.needsConfirmation(candidates.get(0), context.getValueAsString(),
                List.of("未处理的歧义"), candidates);
    }


    /**
     * 修正参数并记录转换链（兼容原有接口）
     */
    public CorrectionResult correctParameterWithAudit(String parameterName, Class<?> parameterType,
                                                      Object originalValue, Parameter parameter,
                                                      String methodName, String executionId) {

        // 使用详细审计方法
        return correctParameterWithDetailedAudit(parameterName, parameterType, originalValue,
                parameter, methodName, executionId);
    }

    /**
     * 批量修正参数并记录转换链
     */
    public Map<String, CorrectionResult> correctParametersWithAudit(Map<String, Object> parameters,
                                                                    Map<String, Class<?>> parameterTypes,
                                                                    Parameter[] reflectionParameters,
                                                                    String methodName, String executionId) {

        Map<String, CorrectionResult> results = new HashMap<>();

        // 为每个参数创建独立的执行ID
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String paramName = entry.getKey();
            Object paramValue = entry.getValue();
            Class<?> paramType = parameterTypes.get(paramName);

            if (paramType == null) {
                results.put(paramName, CorrectionResult.failed(
                        paramValue != null ? paramValue.toString() : "null",
                        "未找到参数类型信息"));
                continue;
            }

            // 查找对应的反射参数
            Parameter reflectionParam = findReflectionParameter(reflectionParameters, paramName);

            // 使用详细审计修正参数
            String paramExecutionId = executionId + "_" + paramName;
            CorrectionResult result = correctParameterWithDetailedAudit(
                    paramName, paramType, paramValue, reflectionParam, methodName, paramExecutionId
            );

            results.put(paramName, result);
        }

        logger.debug("批量参数修正完成: {} 个参数处理完毕", parameters.size());
        return results;
    }

    /**
     * 修正参数上下文并记录转换链
     */
    public CorrectionResult correctParameterWithAudit(ParameterContext context, String executionId) {
        return correctParameterWithDetailedAudit(
                context.parameterName(),
                context.parameterType(),
                context.originalValue(),
                context.parameter(),
                context.methodName(),
                executionId
        );
    }

    /**
     * 获取参数修正统计信息（包含审计信息）
     */
    public Map<String, Object> getEnhancedCorrectionStatistics() {
        Map<String, Object> stats = originalService.getCorrectionStatistics();

        // 添加审计相关统计
        stats.put("auditEnabled", true);
        stats.put("chainRecorderType", parameterChainRecorder.getClass().getSimpleName());

        return stats;
    }

    // 辅助方法
    private ParameterContext updateContext(ParameterContext context, CorrectionResult result) {
        if (result.status() == CorrectionStatus.SUCCESS || result.status() == CorrectionStatus.NEEDS_CONFIRMATION) {
            return new ParameterContext(
                    context.parameterName(),
                    context.parameterType(),
                    result.correctedValue(),
                    context.parameter(),
                    context.methodName(),
                    context.metadata()
            );
        }
        return context;
    }

    private CorrectionResult combineResults(CorrectionResult... results) {
        List<CorrectionResult> validResults = Arrays.stream(results)
                .filter(r -> r.status() != CorrectionStatus.NO_CORRECTION_NEEDED)
                .collect(Collectors.toList());

        if (validResults.isEmpty()) {
            return results[results.length - 1];
        }

        // 找到最终的修正值
        Object finalValue = validResults.get(validResults.size() - 1).correctedValue();
        String originalValue = results[0].originalValue();

        // 合并所有修正操作
        List<String> allCorrections = validResults.stream()
                .flatMap(r -> r.corrections().stream())
                .collect(Collectors.toList());

        // 确定最终状态
        CorrectionStatus finalStatus = validResults.stream()
                .map(CorrectionResult::status)
                .filter(s -> s == CorrectionStatus.FAILED || s == CorrectionStatus.NEEDS_CONFIRMATION)
                .findFirst()
                .orElse(CorrectionStatus.SUCCESS);

        // 计算平均置信度
        double avgConfidence = validResults.stream()
                .mapToDouble(CorrectionResult::confidence)
                .average()
                .orElse(1.0);

        // 合并元数据
        Map<String, Object> combinedMetadata = validResults.stream()
                .flatMap(r -> r.metadata().entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v2 // 后面的值覆盖前面的
                ));

        return new CorrectionResult(finalValue, finalStatus, originalValue, allCorrections, combinedMetadata, avgConfidence);
    }

    private Parameter findReflectionParameter(Parameter[] parameters, String paramName) {
        if (parameters == null) {
            return null;
        }

        for (Parameter param : parameters) {
            if (param.getName().equals(paramName)) {
                return param;
            }
        }

        return null;
    }
}