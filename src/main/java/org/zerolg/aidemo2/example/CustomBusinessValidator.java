package org.zerolg.aidemo2.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.ParameterValidator;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 自定义业务验证器
 * 演示如何添加业务相关的验证逻辑
 */
@Component
public class CustomBusinessValidator implements ParameterValidator {

    private static final Logger logger = LoggerFactory.getLogger(CustomBusinessValidator.class);

    // 身份证号码模式
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^[1-9]\\d{5}(18|19|20)\\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$");

    // 银行卡号模式（简化）
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{16,19}$");

    // 中国手机号码模式
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    public CorrectionResult validate(ParameterContext context) {
        if (!supports(context)) {
            return CorrectionResult.noCorrection(context.originalValue());
        }

        Object value = context.originalValue();
        if (value == null) {
            return CorrectionResult.noCorrection(null);
        }

        String stringValue = value.toString();
        String paramName = context.parameterName().toLowerCase();
        List<String> corrections = new ArrayList<>();

        try {
            // 1. 身份证号码验证
            if (paramName.contains("idcard") || paramName.contains("身份证")) {
                return validateIdCard(stringValue, corrections);
            }

            // 2. 银行卡号验证
            if (paramName.contains("bankcard") || paramName.contains("银行卡")) {
                return validateBankCard(stringValue, corrections);
            }

            // 3. 手机号码验证
            if (paramName.contains("mobile") || paramName.contains("phone") || paramName.contains("手机")) {
                return validateMobile(stringValue, corrections);
            }

            // 4. 邮箱验证增强
            if (paramName.contains("email") || paramName.contains("邮箱")) {
                return validateEmailEnhanced(stringValue, corrections);
            }

            // 5. 用户名验证
            if (paramName.contains("username") || paramName.contains("用户名")) {
                return validateUsername(stringValue, corrections);
            }

            return CorrectionResult.noCorrection(value);

        } catch (Exception e) {
            logger.warn("业务验证失败: paramName={}, value={}", paramName, value, e);
            return CorrectionResult.failed(stringValue, "业务验证异常: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ParameterContext context) {
        if (!String.class.equals(context.parameterType())) {
            return false;
        }

        String paramName = context.parameterName().toLowerCase();
        return paramName.contains("idcard") || paramName.contains("身份证") ||
                paramName.contains("bankcard") || paramName.contains("银行卡") ||
                paramName.contains("mobile") || paramName.contains("phone") || paramName.contains("手机") ||
                paramName.contains("email") || paramName.contains("邮箱") ||
                paramName.contains("username") || paramName.contains("用户名");
    }

    @Override
    public int getPriority() {
        return 80; // 在基础验证之后执行
    }

    /**
     * 验证身份证号码
     */
    private CorrectionResult validateIdCard(String idCard, List<String> corrections) {
        String cleaned = idCard.replaceAll("\\s+", "").toUpperCase();

        if (!cleaned.equals(idCard)) {
            corrections.add("清理身份证号码格式");
        }

        if (!ID_CARD_PATTERN.matcher(cleaned).matches()) {
            return CorrectionResult.failed(idCard, "身份证号码格式不正确");
        }

        // 验证校验位
        if (!validateIdCardChecksum(cleaned)) {
            return CorrectionResult.failed(idCard, "身份证号码校验位不正确");
        }

        if (corrections.isEmpty()) {
            return CorrectionResult.noCorrection(idCard);
        }

        return CorrectionResult.success(cleaned, idCard, corrections, 0.9);
    }

    /**
     * 验证银行卡号
     */
    private CorrectionResult validateBankCard(String bankCard, List<String> corrections) {
        String cleaned = bankCard.replaceAll("\\s+", "");

        if (!cleaned.equals(bankCard)) {
            corrections.add("清理银行卡号格式");
        }

        if (!BANK_CARD_PATTERN.matcher(cleaned).matches()) {
            return CorrectionResult.failed(bankCard, "银行卡号格式不正确");
        }

        // Luhn算法验证（简化版）
        if (!validateLuhn(cleaned)) {
            return CorrectionResult.failed(bankCard, "银行卡号校验失败");
        }

        if (corrections.isEmpty()) {
            return CorrectionResult.noCorrection(bankCard);
        }

        return CorrectionResult.success(cleaned, bankCard, corrections, 0.9);
    }

    /**
     * 验证手机号码
     */
    private CorrectionResult validateMobile(String mobile, List<String> corrections) {
        String cleaned = mobile.replaceAll("[^\\d]", "");

        // 移除国际区号
        if (cleaned.startsWith("86") && cleaned.length() == 13) {
            cleaned = cleaned.substring(2);
            corrections.add("移除国际区号");
        }

        if (!cleaned.equals(mobile)) {
            corrections.add("清理手机号码格式");
        }

        if (!MOBILE_PATTERN.matcher(cleaned).matches()) {
            return CorrectionResult.failed(mobile, "手机号码格式不正确");
        }

        if (corrections.isEmpty()) {
            return CorrectionResult.noCorrection(mobile);
        }

        return CorrectionResult.success(cleaned, mobile, corrections, 0.9);
    }

    /**
     * 增强邮箱验证
     */
    private CorrectionResult validateEmailEnhanced(String email, List<String> corrections) {
        String cleaned = email.trim().toLowerCase();

        if (!cleaned.equals(email)) {
            corrections.add("标准化邮箱格式");
        }

        // 基本邮箱格式验证
        if (!cleaned.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return CorrectionResult.failed(email, "邮箱格式不正确");
        }

        // 检查常见的邮箱域名拼写错误
        String correctedDomain = correctCommonEmailDomains(cleaned);
        if (!correctedDomain.equals(cleaned)) {
            corrections.add("修正邮箱域名拼写");
            cleaned = correctedDomain;
        }

        if (corrections.isEmpty()) {
            return CorrectionResult.noCorrection(email);
        }

        return CorrectionResult.success(cleaned, email, corrections, 0.85);
    }

    /**
     * 验证用户名
     */
    private CorrectionResult validateUsername(String username, List<String> corrections) {
        String cleaned = username.trim();

        if (!cleaned.equals(username)) {
            corrections.add("清理用户名格式");
        }

        // 用户名规则：3-20位，字母数字下划线
        if (!cleaned.matches("^[a-zA-Z0-9_]{3,20}$")) {
            return CorrectionResult.failed(username, "用户名格式不正确（3-20位字母数字下划线）");
        }

        // 不能以数字开头
        if (cleaned.matches("^\\d.*")) {
            return CorrectionResult.failed(username, "用户名不能以数字开头");
        }

        if (corrections.isEmpty()) {
            return CorrectionResult.noCorrection(username);
        }

        return CorrectionResult.success(cleaned, username, corrections, 0.9);
    }

    /**
     * 验证身份证校验位
     */
    private boolean validateIdCardChecksum(String idCard) {
        if (idCard.length() != 18) return false;

        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checksums = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += Character.getNumericValue(idCard.charAt(i)) * weights[i];
        }

        char expectedChecksum = checksums[sum % 11];
        return idCard.charAt(17) == expectedChecksum;
    }

    /**
     * Luhn算法验证
     */
    private boolean validateLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;

        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return sum % 10 == 0;
    }

    /**
     * 修正常见邮箱域名拼写错误
     */
    private String correctCommonEmailDomains(String email) {
        String[] parts = email.split("@");
        if (parts.length != 2) return email;

        String domain = parts[1];

        // 常见拼写错误修正
        switch (domain) {
            case "gmai.com", "gmial.com", "gmail.co" -> domain = "gmail.com";
            case "163.co", "16.com" -> domain = "163.com";
            case "qq.co", "q.com" -> domain = "qq.com";
            case "hotmai.com", "hotmial.com" -> domain = "hotmail.com";
        }

        return parts[0] + "@" + domain;
    }
}