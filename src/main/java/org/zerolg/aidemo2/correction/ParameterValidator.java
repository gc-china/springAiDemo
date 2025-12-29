package org.zerolg.aidemo2.correction;

import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

/**
 * 参数验证器接口
 */
public interface ParameterValidator {

    /**
     * 验证参数
     *
     * @param context 参数上下文
     * @return 验证结果
     */
    CorrectionResult validate(ParameterContext context);

    /**
     * 检查是否支持该参数
     *
     * @param context 参数上下文
     * @return 是否支持
     */
    boolean supports(ParameterContext context);

    /**
     * 获取验证器的优先级
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }
}