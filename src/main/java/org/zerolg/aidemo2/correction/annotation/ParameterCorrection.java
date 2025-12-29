package org.zerolg.aidemo2.correction.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 参数修正注解
 * 标记需要进行参数修正的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParameterCorrection {

    /**
     * 是否在修正失败时抛出异常
     * 默认为false，即修正失败时使用原始参数继续执行
     */
    boolean failOnError() default false;

    /**
     * 是否自动确认需要确认的修正
     * 默认为true，即自动应用第一个候选结果
     */
    boolean autoConfirm() default true;

    /**
     * 是否启用交互模式
     * 启用时，需要确认的参数会触发用户交互流程
     */
    boolean interactiveMode() default false;

    /**
     * 是否记录修正失败的参数
     * 默认为true，记录失败信息到日志
     */
    boolean logFailures() default true;

    /**
     * 最小置信度阈值
     * 只有置信度高于此值的修正才会被应用
     */
    double minConfidence() default 0.5;

    /**
     * 修正模式
     */
    CorrectionMode mode() default CorrectionMode.FULL;

    /**
     * 排除的参数名称
     * 这些参数不会被修正
     */
    String[] excludeParameters() default {};

    /**
     * 只包含的参数名称
     * 如果指定，只有这些参数会被修正
     */
    String[] includeParameters() default {};

    /**
     * 修正模式枚举
     */
    enum CorrectionMode {
        /**
         * 完整修正：标准化 + 实体解析 + 验证
         */
        FULL,

        /**
         * 仅标准化：只进行基础的字符串清理和格式化
         */
        NORMALIZE_ONLY,

        /**
         * 仅验证：只进行参数验证，不修改值
         */
        VALIDATE_ONLY,

        /**
         * 标准化和验证：跳过实体解析
         */
        NORMALIZE_AND_VALIDATE
    }
}