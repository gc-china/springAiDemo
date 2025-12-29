package org.zerolg.aidemo2.correction.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.ParameterCorrectionService;
import org.zerolg.aidemo2.correction.annotation.ParameterCorrection;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.CorrectionStatus;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数修正切面
 * 自动拦截带有@ParameterCorrection注解的方法，对参数进行修正
 */
@Aspect
@Component
public class ArgumentCorrectionAspect {

    private static final Logger logger = LoggerFactory.getLogger(ArgumentCorrectionAspect.class);

    private final ParameterCorrectionService correctionService;

    @Autowired
    public ArgumentCorrectionAspect(ParameterCorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    /**
     * 拦截带有@ParameterCorrection注解的方法
     */
    @Around("@annotation(parameterCorrection)")
    public Object correctParameters(ProceedingJoinPoint joinPoint, ParameterCorrection parameterCorrection) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();

        logger.debug("开始参数修正: method={}, args={}", method.getName(), args.length);

        try {
            // 1. 构建参数上下文
            Map<String, Object> parameterMap = buildParameterMap(parameters, args);
            Map<String, Class<?>> parameterTypes = buildParameterTypeMap(parameters);

            // 2. 执行参数修正
            Map<String, CorrectionResult> correctionResults = correctionService.correctParameters(
                    parameterMap, parameterTypes, parameters, method.getName());

            // 3. 处理修正结果
            Object[] correctedArgs = processCorrectionResults(args, parameters, correctionResults, parameterCorrection);

            // 4. 记录修正信息
            logCorrectionResults(method.getName(), correctionResults);

            // 5. 执行原方法
            return joinPoint.proceed(correctedArgs);

        } catch (Exception e) {
            logger.error("参数修正过程异常: method={}", method.getName(), e);

            // 根据配置决定是否继续执行
            if (parameterCorrection.failOnError()) {
                throw new RuntimeException("参数修正失败: " + e.getMessage(), e);
            } else {
                // 使用原始参数继续执行
                return joinPoint.proceed();
            }
        }
    }

    /**
     * 构建参数映射
     */
    private Map<String, Object> buildParameterMap(Parameter[] parameters, Object[] args) {
        Map<String, Object> parameterMap = new HashMap<>();

        for (int i = 0; i < parameters.length && i < args.length; i++) {
            String paramName = parameters[i].getName();
            Object paramValue = args[i];
            parameterMap.put(paramName, paramValue);
        }

        return parameterMap;
    }

    /**
     * 构建参数类型映射
     */
    private Map<String, Class<?>> buildParameterTypeMap(Parameter[] parameters) {
        Map<String, Class<?>> typeMap = new HashMap<>();

        for (Parameter parameter : parameters) {
            typeMap.put(parameter.getName(), parameter.getType());
        }

        return typeMap;
    }

    /**
     * 处理修正结果
     */
    private Object[] processCorrectionResults(Object[] originalArgs, Parameter[] parameters,
                                              Map<String, CorrectionResult> correctionResults,
                                              ParameterCorrection config) {

        Object[] correctedArgs = originalArgs.clone();
        List<String> confirmationNeeded = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            CorrectionResult result = correctionResults.get(paramName);

            if (result == null) {
                continue;
            }

            switch (result.status()) {
                case SUCCESS:
                    // 应用修正
                    correctedArgs[i] = result.correctedValue();
                    logger.debug("参数修正成功: {}={} -> {}", paramName, originalArgs[i], result.correctedValue());
                    break;

                case NEEDS_CONFIRMATION:
                    confirmationNeeded.add(paramName);
                    if (config.autoConfirm()) {
                        // 自动确认，使用第一个候选
                        correctedArgs[i] = result.correctedValue();
                        logger.warn("参数需要确认但已自动应用: {}={} -> {}", paramName, originalArgs[i], result.correctedValue());
                    } else {
                        logger.warn("参数需要用户确认: {}={}, 候选: {}", paramName, originalArgs[i], result.metadata().get("candidates"));
                    }
                    break;

                case FAILED:
                    failures.add(paramName);
                    logger.error("参数修正失败: {}={}, 原因: {}", paramName, originalArgs[i], result.metadata().get("reason"));

                    if (config.failOnError()) {
                        throw new RuntimeException(String.format("参数修正失败: %s, 原因: %s",
                                paramName, result.metadata().get("reason")));
                    }
                    break;

                case NO_CORRECTION_NEEDED:
                    // 无需修正，保持原值
                    break;
            }
        }

        // 处理需要确认的参数
        if (!confirmationNeeded.isEmpty() && !config.autoConfirm()) {
            handleConfirmationNeeded(confirmationNeeded, correctionResults, config);
        }

        // 处理失败的参数
        if (!failures.isEmpty() && config.logFailures()) {
            logger.warn("以下参数修正失败: {}", failures);
        }

        return correctedArgs;
    }

    /**
     * 处理需要确认的参数
     */
    private void handleConfirmationNeeded(List<String> confirmationNeeded,
                                          Map<String, CorrectionResult> correctionResults,
                                          ParameterCorrection config) {

        if (config.interactiveMode()) {
            // 交互模式：记录需要确认的参数，可以通过其他机制处理
            logger.info("交互模式：以下参数需要用户确认: {}", confirmationNeeded);

            // 这里可以集成用户交互机制，比如：
            // 1. 发送通知给前端
            // 2. 记录到待确认队列
            // 3. 触发用户确认流程

        } else {
            // 非交互模式：记录警告
            logger.warn("以下参数需要确认但未启用交互模式: {}", confirmationNeeded);
        }
    }

    /**
     * 记录修正结果
     */
    private void logCorrectionResults(String methodName, Map<String, CorrectionResult> results) {
        int successCount = 0;
        int confirmationCount = 0;
        int failureCount = 0;
        int noChangeCount = 0;

        for (CorrectionResult result : results.values()) {
            switch (result.status()) {
                case SUCCESS -> successCount++;
                case NEEDS_CONFIRMATION -> confirmationCount++;
                case FAILED -> failureCount++;
                case NO_CORRECTION_NEEDED -> noChangeCount++;
            }
        }

        if (successCount > 0 || confirmationCount > 0 || failureCount > 0) {
            logger.info("参数修正完成: method={}, 成功={}, 需确认={}, 失败={}, 无变化={}",
                    methodName, successCount, confirmationCount, failureCount, noChangeCount);
        }

        // 详细日志
        if (logger.isDebugEnabled()) {
            for (Map.Entry<String, CorrectionResult> entry : results.entrySet()) {
                CorrectionResult result = entry.getValue();
                if (result.status() != CorrectionStatus.NO_CORRECTION_NEEDED) {
                    logger.debug("参数修正详情: {}={} -> {}, 状态={}, 置信度={}, 修正操作={}",
                            entry.getKey(), result.originalValue(), result.correctedValue(),
                            result.status(), result.confidence(), result.corrections());
                }
            }
        }
    }
}