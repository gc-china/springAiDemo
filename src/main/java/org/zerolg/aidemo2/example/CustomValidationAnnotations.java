package org.zerolg.aidemo2.example;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义验证注解集合
 * 演示如何创建业务相关的验证注解
 */
public class CustomValidationAnnotations {

    /**
     * 中国身份证号码验证注解
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ChineseIdCard {
        String message() default "身份证号码格式不正确";
    }

    /**
     * 中国手机号码验证注解
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ChineseMobile {
        String message() default "手机号码格式不正确";

        boolean allowInternational() default false; // 是否允许国际格式
    }

    /**
     * 银行卡号验证注解
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BankCard {
        String message() default "银行卡号格式不正确";

        boolean enableLuhnCheck() default true; // 是否启用Luhn算法验证
    }

    /**
     * 用户名验证注解
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Username {
        String message() default "用户名格式不正确";

        int minLength() default 3;

        int maxLength() default 20;

        boolean allowNumbers() default true;

        boolean allowUnderscore() default true;
    }

    /**
     * 强密码验证注解
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface StrongPassword {
        String message() default "密码强度不够";

        int minLength() default 8;

        boolean requireUppercase() default true;

        boolean requireLowercase() default true;

        boolean requireNumbers() default true;

        boolean requireSpecialChars() default true;
    }

    /**
     * 中文姓名验证注解
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ChineseName {
        String message() default "中文姓名格式不正确";

        int minLength() default 2;

        int maxLength() default 10;
    }

    /**
     * 业务状态验证注解
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BusinessStatus {
        String message() default "业务状态不正确";

        String[] allowedValues() default {}; // 允许的状态值

        boolean caseSensitive() default false;
    }

    /**
     * 金额验证注解
     */
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Amount {
        String message() default "金额格式不正确";

        double min() default 0.0;

        double max() default Double.MAX_VALUE;

        int decimalPlaces() default 2; // 小数位数

        String currency() default "CNY"; // 货币类型
    }
}