package org.zerolg.aidemo2.audit.model;

import java.util.List;
import java.util.Map;

/**
 * 参数转换链
 */
public record ParameterChain(
        String executionId,                    // 执行ID
        List<ParameterTransformation> steps,   // 转换步骤
        Map<String, Object> originalParams,    // 原始参数
        Map<String, Object> finalParams,       // 最终参数
        double overallConfidence,              // 整体置信度
        List<String> appliedRules             // 应用的规则
) {
    public static ParameterChain create(String executionId, Map<String, Object> originalParams) {
        return new ParameterChain(
                executionId,
                List.of(),
                originalParams,
                originalParams,
                1.0,
                List.of()
        );
    }

    public ParameterChain addTransformation(ParameterTransformation transformation) {
        var newSteps = new java.util.ArrayList<>(steps);
        newSteps.add(transformation);

        var newFinalParams = new java.util.HashMap<>(finalParams);
        newFinalParams.put(transformation.parameterName(), transformation.transformedValue());

        var newRules = new java.util.ArrayList<>(appliedRules);
        if (transformation.metadata().containsKey("rule")) {
            newRules.add(transformation.metadata().get("rule").toString());
        }

        return new ParameterChain(
                executionId,
                newSteps,
                originalParams,
                newFinalParams,
                Math.min(overallConfidence, transformation.confidence()),
                newRules
        );
    }

    public ParameterChain withSteps(List<ParameterTransformation> newSteps) {
        // 计算最终参数
        var newFinalParams = new java.util.HashMap<>(originalParams);
        for (ParameterTransformation step : newSteps) {
            newFinalParams.put(step.parameterName(), step.transformedValue());
        }

        // 计算整体置信度
        double newConfidence = newSteps.isEmpty() ? 1.0 :
                newSteps.stream().mapToDouble(ParameterTransformation::confidence).min().orElse(1.0);

        // 收集应用的规则
        var newRules = newSteps.stream()
                .filter(step -> step.metadata().containsKey("rule"))
                .map(step -> step.metadata().get("rule").toString())
                .collect(java.util.stream.Collectors.toList());

        return new ParameterChain(
                executionId,
                newSteps,
                originalParams,
                newFinalParams,
                newConfidence,
                newRules
        );
    }
}