package org.zerolg.aidemo2.correction.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义正数验证注解
 * 用于标记参数必须为正数
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PositiveNumber {

    /**
     * 错误消息
     */
    String message() default "数值必须为正数";

    /**
     * 是否包含零（默认不包含，即必须大于0）
     */
    boolean includeZero() default false;
}