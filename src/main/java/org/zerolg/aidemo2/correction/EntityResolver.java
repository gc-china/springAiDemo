package org.zerolg.aidemo2.correction;

import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.util.List;

/**
 * 实体解析器接口
 */
public interface EntityResolver {

    /**
     * 解析实体
     *
     * @param context 参数上下文
     * @return 解析结果
     */
    CorrectionResult resolve(ParameterContext context);

    /**
     * 模糊匹配实体
     *
     * @param input      输入值
     * @param entityType 实体类型
     * @return 匹配的候选实体列表
     */
    List<Object> fuzzyMatch(String input, Class<?> entityType);

    /**
     * 检查是否支持该实体类型
     *
     * @param entityType 实体类型
     * @return 是否支持
     */
    boolean supports(Class<?> entityType);

    /**
     * 获取解析器的优先级
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }
}