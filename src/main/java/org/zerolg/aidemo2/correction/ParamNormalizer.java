package org.zerolg.aidemo2.correction;

import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

/**
 * 参数标准化器接口
 */
public interface ParamNormalizer {

    /**
     * 标准化参数
     *
     * @param context 参数上下文
     * @return 标准化结果
     */
    CorrectionResult normalize(ParameterContext context);

    /**
     * 检查是否支持该参数类型
     *
     * @param context 参数上下文
     * @return 是否支持
     */
    boolean supports(ParameterContext context);

    /**
     * 获取标准化器的优先级（数字越小优先级越高）
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }
}