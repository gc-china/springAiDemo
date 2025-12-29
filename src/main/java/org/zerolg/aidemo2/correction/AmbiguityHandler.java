package org.zerolg.aidemo2.correction;

import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.util.List;

/**
 * 歧义处理器接口
 * 处理多候选结果的歧义情况
 */
public interface AmbiguityHandler {

    /**
     * 处理歧义情况
     *
     * @param context    参数上下文
     * @param candidates 候选结果列表
     * @return 处理后的结果
     */
    CorrectionResult handleAmbiguity(ParameterContext context, List<Object> candidates);

    /**
     * 检查是否支持该类型的歧义处理
     *
     * @param context 参数上下文
     * @return 是否支持
     */
    boolean supports(ParameterContext context);

    /**
     * 获取处理器的优先级
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }
}