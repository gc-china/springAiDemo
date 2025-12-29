package org.zerolg.aidemo2.example;

import jakarta.validation.constraints.Email;
import org.springframework.web.bind.annotation.*;
import org.zerolg.aidemo2.correction.annotation.ParameterCorrection;
import org.zerolg.aidemo2.correction.annotation.PositiveNumber;
import org.zerolg.aidemo2.example.CustomValidationAnnotations.*;

import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 完整的参数修正系统使用示例
 * 展示所有功能的综合应用
 */
@RestController
@RequestMapping("/api/example")
public class CompleteExampleController {

    /**
     * 用户注册示例 - 展示多种参数修正
     */
    @PostMapping("/register")
    @ParameterCorrection(
            failOnError = false,
            autoConfirm = true,
            minConfidence = 0.6,
            logFailures = true
    )
    public Map<String, Object> registerUser(
            @RequestParam @ChineseName String realName,           // 自定义中文姓名验证
            @RequestParam @Username String username,              // 自定义用户名验证
            @RequestParam @StrongPassword String password,        // 自定义强密码验证
            @RequestParam @ChineseMobile String mobile,           // 自定义手机号验证
            @RequestParam @Email String email,                    // 标准邮箱验证
            @RequestParam @ChineseIdCard String idCard,           // 自定义身份证验证
            @RequestParam @Past LocalDate birthDate,              // 过去日期验证
            @RequestParam @BusinessStatus(allowedValues = {"ACTIVE", "INACTIVE"}) String status) {

        Map<String, Object> result = new HashMap<>();
        result.put("realName", realName);
        result.put("username", username);
        result.put("mobile", mobile);
        result.put("email", email);
        result.put("idCard", idCard);
        result.put("birthDate", birthDate);
        result.put("status", status);
        result.put("message", "用户注册成功");

        return result;
    }

    /**
     * 订单创建示例 - 展示数值和金额处理
     */
    @PostMapping("/orders")
    @ParameterCorrection(
            mode = ParameterCorrection.CorrectionMode.FULL,
            minConfidence = 0.7
    )
    public Map<String, Object> createOrder(
            @RequestParam String productName,                     // 基础字符串清理
            @RequestParam @PositiveNumber Integer quantity,       // 正数验证
            @RequestParam @Amount(min = 0.01, max = 999999.99) Double price, // 金额验证
            @RequestParam @ChineseMobile String customerPhone,    // 手机号验证
            @RequestParam String deliveryAddress,                 // 地址清理
            @RequestParam @BusinessStatus(allowedValues = {"PENDING", "CONFIRMED", "CANCELLED"}) String orderStatus) {

        Map<String, Object> result = new HashMap<>();
        result.put("productName", productName);
        result.put("quantity", quantity);
        result.put("price", price);
        result.put("customerPhone", customerPhone);
        result.put("deliveryAddress", deliveryAddress);
        result.put("orderStatus", orderStatus);
        result.put("totalAmount", price * quantity);
        result.put("message", "订单创建成功");

        return result;
    }

    /**
     * 支付信息示例 - 展示敏感信息处理
     */
    @PostMapping("/payment")
    @ParameterCorrection(
            failOnError = true,  // 支付信息必须验证成功
            minConfidence = 0.9  // 高置信度要求
    )
    public Map<String, Object> processPayment(
            @RequestParam @BankCard String bankCardNumber,        // 银行卡号验证
            @RequestParam @Size(min = 3, max = 4) String cvv,     // CVV验证
            @RequestParam @Amount(min = 0.01) Double amount,      // 支付金额
            @RequestParam String paymentMethod) {                 // 支付方式

        Map<String, Object> result = new HashMap<>();
        result.put("bankCardNumber", maskBankCard(bankCardNumber)); // 脱敏处理
        result.put("amount", amount);
        result.put("paymentMethod", paymentMethod);
        result.put("message", "支付处理成功");

        return result;
    }

    /**
     * 数据导入示例 - 展示批量数据清理
     */
    @PostMapping("/import")
    @ParameterCorrection(
            mode = ParameterCorrection.CorrectionMode.NORMALIZE_ONLY, // 只标准化，不验证
            autoConfirm = true
    )
    public Map<String, Object> importData(
            @RequestParam String csvData,                         // CSV数据清理
            @RequestParam String encoding,                        // 编码处理
            @RequestParam Boolean hasHeader,                      // 布尔值解析
            @RequestParam String dateFormat) {                    // 日期格式标准化

        Map<String, Object> result = new HashMap<>();
        result.put("csvData", csvData);
        result.put("encoding", encoding);
        result.put("hasHeader", hasHeader);
        result.put("dateFormat", dateFormat);
        result.put("message", "数据导入预处理完成");

        return result;
    }

    /**
     * 搜索示例 - 展示搜索参数优化
     */
    @GetMapping("/search")
    @ParameterCorrection(
            excludeParameters = {"page", "size"}, // 排除分页参数
            minConfidence = 0.5
    )
    public Map<String, Object> searchData(
            @RequestParam String keyword,                         // 搜索关键词清理
            @RequestParam(required = false) String category,      // 分类标准化
            @RequestParam(required = false) @PositiveNumber(includeZero = true) Double minPrice, // 最小价格
            @RequestParam(required = false) @PositiveNumber Double maxPrice,     // 最大价格
            @RequestParam(defaultValue = "0") Integer page,       // 不会被修正
            @RequestParam(defaultValue = "10") Integer size) {    // 不会被修正

        Map<String, Object> result = new HashMap<>();
        result.put("keyword", keyword);
        result.put("category", category);
        result.put("minPrice", minPrice);
        result.put("maxPrice", maxPrice);
        result.put("page", page);
        result.put("size", size);
        result.put("message", "搜索参数处理完成");

        return result;
    }

    /**
     * 银行卡号脱敏处理
     */
    private String maskBankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 8) {
            return bankCard;
        }

        String prefix = bankCard.substring(0, 4);
        String suffix = bankCard.substring(bankCard.length() - 4);
        String middle = "*".repeat(bankCard.length() - 8);

        return prefix + middle + suffix;
    }
}