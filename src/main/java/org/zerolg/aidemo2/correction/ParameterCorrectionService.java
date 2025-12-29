package org.zerolg.aidemo2.correction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.CorrectionStatus;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 参数修正服务
 * 协调各个修正组件，提供完整的参数修正流程
 */
@Service
public class ParameterCorrectionService {

    private static final Logger logger = LoggerFactory.getLogger(ParameterCorrectionService.class);

    private final List<ParamNormalizer> normalizers;
    private final List<EntityResolver> entityResolvers;
    private final List<ParameterValidator> validators;
    private final List<AmbiguityHandler> ambiguityHandlers;

    @Autowired
    public ParameterCorrectionService(
            List<ParamNormalizer> normalizers,
            List<EntityResolver> entityResolvers,
            List<ParameterValidator> validators,
            List<AmbiguityHandler> ambiguityHandlers) {

        // 按优先级排序
        this.normalizers = normalizers.stream()
                .sorted(Comparator.comparing(ParamNormalizer::getPriority))
                .collect(Collectors.toList());

        this.entityResolvers = entityResolvers.stream()
                .sorted(Comparator.comparing(EntityResolver::getPriority))
                .collect(Collectors.toList());

        this.validators = validators.stream()
                .sorted(Comparator.comparing(ParameterValidator::getPriority))
                .collect(Collectors.toList());

        this.ambiguityHandlers = ambiguityHandlers.stream()
                .sorted(Comparator.comparing(AmbiguityHandler::getPriority))
                .collect(Collectors.toList());

        logger.info("参数修正服务初始化完成: normalizers={}, entityResolvers={}, validators={}, ambiguityHandlers={}",
                normalizers.size(), entityResolvers.size(), validators.size(), ambiguityHandlers.size());
    }

    /**
     * 修正单个参数
     *
     * @param parameterName 参数名称
     * @param parameterType 参数类型
     * @param originalValue 原始值
     * @param parameter     反射参数信息
     * @param methodName    方法名称
     * @return 修正结果
     */
    public CorrectionResult correctParameter(String parameterName, Class<?> parameterType,
                                             Object originalValue, Parameter parameter, String methodName) {

        ParameterContext context = ParameterContext.create(
                parameterName, parameterType, originalValue, parameter, methodName);

        return correctParameter(context);
    }

    /**
     * 修正参数（使用上下文）
     *
     * @param context 参数上下文
     * @return 修正结果
     */
    public CorrectionResult correctParameter(ParameterContext context) {
        logger.debug("开始修正参数: name={}, type={}, value={}",
                context.parameterName(), context.parameterType().getSimpleName(), context.originalValue());

        try {
            // 阶段1: 标准化
            CorrectionResult normalizationResult = applyNormalization(context);
            if (normalizationResult.status() == CorrectionStatus.FAILED) {
                return normalizationResult;
            }

            // 更新上下文
            context = updateContext(context, normalizationResult);

            // 阶段2: 实体解析
            CorrectionResult resolutionResult = applyEntityResolution(context);
            if (resolutionResult.status() == CorrectionStatus.FAILED) {
                return combineResults(normalizationResult, resolutionResult);
            }

            // 处理多候选情况
            if (resolutionResult.status() == CorrectionStatus.NEEDS_CONFIRMATION) {
                @SuppressWarnings("unchecked")
                List<Object> candidates = (List<Object>) resolutionResult.metadata().get("candidates");
                CorrectionResult ambiguityResult = handleAmbiguity(context, candidates);
                return combineResults(normalizationResult, ambiguityResult);
            }

            // 更新上下文
            context = updateContext(context, resolutionResult);

            // 阶段3: 验证
            CorrectionResult validationResult = applyValidation(context);

            // 合并所有结果
            return combineResults(normalizationResult, resolutionResult, validationResult);

        } catch (Exception e) {
            logger.error("参数修正异常: context={}", context.parameterName(), e);
            return CorrectionResult.failed(context.getValueAsString(), "参数修正异常: " + e.getMessage());
        }
    }

    /**
     * 批量修正参数
     *
     * @param parameters     参数映射
     * @param parameterTypes 参数类型映射
     * @param parameters     反射参数信息数组
     * @param methodName     方法名称
     * @return 修正结果映射
     */
    public Map<String, CorrectionResult> correctParameters(Map<String, Object> parameters,
                                                           Map<String, Class<?>> parameterTypes,
                                                           Parameter[] reflectionParameters,
                                                           String methodName) {

        Map<String, CorrectionResult> results = new HashMap<>();

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

            CorrectionResult result = correctParameter(paramName, paramType, paramValue, reflectionParam, methodName);
            results.put(paramName, result);
        }

        return results;
    }

    /**
     * 应用标准化
     */
    private CorrectionResult applyNormalization(ParameterContext context) {
        for (ParamNormalizer normalizer : normalizers) {
            if (normalizer.supports(context)) {
                CorrectionResult result = normalizer.normalize(context);
                if (result.status() != CorrectionStatus.NO_CORRECTION_NEEDED) {
                    logger.debug("标准化应用: normalizer={}, result={}",
                            normalizer.getClass().getSimpleName(), result.status());
                    return result;
                }
            }
        }

        return CorrectionResult.noCorrection(context.originalValue());
    }

    /**
     * 应用实体解析
     */
    private CorrectionResult applyEntityResolution(ParameterContext context) {
        for (EntityResolver resolver : entityResolvers) {
            if (resolver.supports(context.parameterType())) {
                CorrectionResult result = resolver.resolve(context);
                if (result.status() != CorrectionStatus.NO_CORRECTION_NEEDED) {
                    logger.debug("实体解析应用: resolver={}, result={}",
                            resolver.getClass().getSimpleName(), result.status());
                    return result;
                }
            }
        }

        return CorrectionResult.noCorrection(context.originalValue());
    }

    /**
     * 应用验证
     */
    private CorrectionResult applyValidation(ParameterContext context) {
        for (ParameterValidator validator : validators) {
            if (validator.supports(context)) {
                CorrectionResult result = validator.validate(context);
                if (result.status() != CorrectionStatus.NO_CORRECTION_NEEDED) {
                    logger.debug("验证应用: validator={}, result={}",
                            validator.getClass().getSimpleName(), result.status());
                    return result;
                }
            }
        }

        return CorrectionResult.noCorrection(context.originalValue());
    }

    /**
     * 处理歧义
     */
    private CorrectionResult handleAmbiguity(ParameterContext context, List<Object> candidates) {
        for (AmbiguityHandler handler : ambiguityHandlers) {
            if (handler.supports(context)) {
                CorrectionResult result = handler.handleAmbiguity(context, candidates);
                logger.debug("歧义处理应用: handler={}, result={}",
                        handler.getClass().getSimpleName(), result.status());
                return result;
            }
        }

        // 默认返回需要确认的结果
        return CorrectionResult.needsConfirmation(candidates.get(0), context.getValueAsString(),
                Arrays.asList("未处理的歧义"), candidates);
    }

    /**
     * 更新上下文
     */
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

    /**
     * 合并修正结果
     */
    private CorrectionResult combineResults(CorrectionResult... results) {
        List<CorrectionResult> validResults = Arrays.stream(results)
                .filter(r -> r.status() != CorrectionStatus.NO_CORRECTION_NEEDED)
                .collect(Collectors.toList());

        if (validResults.isEmpty()) {
            return results[results.length - 1]; // 返回最后一个结果
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

    /**
     * 查找反射参数
     */
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

    /**
     * 获取修正统计信息
     */
    public Map<String, Object> getCorrectionStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("normalizers", normalizers.size());
        stats.put("entityResolvers", entityResolvers.size());
        stats.put("validators", validators.size());
        stats.put("ambiguityHandlers", ambiguityHandlers.size());

        // 添加各组件的详细信息
        stats.put("normalizerTypes", normalizers.stream()
                .map(n -> n.getClass().getSimpleName())
                .collect(Collectors.toList()));

        stats.put("entityResolverTypes", entityResolvers.stream()
                .map(r -> r.getClass().getSimpleName())
                .collect(Collectors.toList()));

        stats.put("validatorTypes", validators.stream()
                .map(v -> v.getClass().getSimpleName())
                .collect(Collectors.toList()));

        stats.put("ambiguityHandlerTypes", ambiguityHandlers.stream()
                .map(h -> h.getClass().getSimpleName())
                .collect(Collectors.toList()));

        return stats;
    }
}