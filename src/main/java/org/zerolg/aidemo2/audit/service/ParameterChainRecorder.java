package org.zerolg.aidemo2.audit.service;

import org.zerolg.aidemo2.audit.model.ParameterChain;
import org.zerolg.aidemo2.audit.model.ParameterPatternAnalysis;
import org.zerolg.aidemo2.audit.model.ParameterQuery;

import java.time.Duration;
import java.util.List;

/**
 * 参数链记录器接口
 */
public interface ParameterChainRecorder {

    /**
     * 记录参数转换链
     */
    void recordParameterChain(String executionId, ParameterChain chain);

    /**
     * 查询参数转换历史
     */
    List<ParameterChain> queryParameterHistory(ParameterQuery query);

    /**
     * 分析参数转换模式
     */
    ParameterPatternAnalysis analyzePatterns(String toolName, Duration timeRange);

    /**
     * 获取参数转换统计
     */
    java.util.Map<String, Object> getTransformationStats(String toolName);

    /**
     * 获取最常见的参数转换
     */
    List<org.zerolg.aidemo2.audit.model.ParameterTransformation> getMostCommonTransformations(String toolName, int limit);
}