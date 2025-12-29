package org.zerolg.aidemo2.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.zerolg.aidemo2.correction.ParameterCorrectionService;
import org.zerolg.aidemo2.correction.annotation.ParameterCorrection;

import javax.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.zerolg.aidemo2.correction.annotation.PositiveNumber;

/**
 * 参数修正测试控制器
 * 用于演示和测试参数修正系统的功能
 */
@RestController
@RequestMapping("/api/test/parameter-correction")
public class ParameterCorrectionTestController {

    @Autowired
    private ParameterCorrectionService correctionService;

    /**
     * 测试字符串标准化
     */
    @PostMapping("/string-normalization")
    @ParameterCorrection
    public Map<String, Object> testStringNormalization(
            @RequestParam String name,
            @RequestParam String description) {

        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("description", description);
        result.put("message", "字符串标准化测试完成");

        return result;
    }

    /**
     * 测试数值标准化
     */
    @PostMapping("/number-normalization")
    @ParameterCorrection
    public Map<String, Object> testNumberNormalization(
            @RequestParam @Min(0) @Max(1000) Integer age,
            @RequestParam @DecimalMin("0.0") @DecimalMax("100.0") Double percentage,
            @RequestParam @PositiveNumber Long amount) {

        Map<String, Object> result = new HashMap<>();
        result.put("age", age);
        result.put("percentage", percentage);
        result.put("amount", amount);
        result.put("message", "数值标准化测试完成");

        return result;
    }

    /**
     * 测试日期标准化
     */
    @PostMapping("/date-normalization")
    @ParameterCorrection
    public Map<String, Object> testDateNormalization(
            @RequestParam @Past LocalDate birthDate,
            @RequestParam @Future LocalDateTime appointmentTime) {

        Map<String, Object> result = new HashMap<>();
        result.put("birthDate", birthDate);
        result.put("appointmentTime", appointmentTime);
        result.put("message", "日期标准化测试完成");

        return result;
    }

    /**
     * 测试布尔值解析
     */
    @PostMapping("/boolean-resolution")
    @ParameterCorrection
    public Map<String, Object> testBooleanResolution(
            @RequestParam Boolean isActive,
            @RequestParam Boolean isEnabled) {

        Map<String, Object> result = new HashMap<>();
        result.put("isActive", isActive);
        result.put("isEnabled", isEnabled);
        result.put("message", "布尔值解析测试完成");

        return result;
    }

    /**
     * 测试综合修正
     */
    @PostMapping("/comprehensive")
    @ParameterCorrection(
            failOnError = false,
            autoConfirm = true,
            minConfidence = 0.6
    )
    public Map<String, Object> testComprehensiveCorrection(
            @RequestParam String name,
            @RequestParam @Min(18) @Max(120) Integer age,
            @RequestParam @Size(min = 5, max = 100) String email,
            @RequestParam Boolean isVip,
            @RequestParam @PositiveNumber(includeZero = true) Double balance,
            @RequestParam @Past LocalDate registrationDate) {

        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("age", age);
        result.put("email", email);
        result.put("isVip", isVip);
        result.put("balance", balance);
        result.put("registrationDate", registrationDate);
        result.put("message", "综合修正测试完成");

        return result;
    }

    /**
     * 测试修正失败处理
     */
    @PostMapping("/failure-handling")
    @ParameterCorrection(
            failOnError = false,
            logFailures = true
    )
    public Map<String, Object> testFailureHandling(
            @RequestParam String invalidData) {

        Map<String, Object> result = new HashMap<>();
        result.put("invalidData", invalidData);
        result.put("message", "失败处理测试完成");

        return result;
    }

    /**
     * 获取修正系统统计信息
     */
    @GetMapping("/statistics")
    public Map<String, Object> getCorrectionStatistics() {
        return correctionService.getCorrectionStatistics();
    }

    /**
     * 测试不同修正模式
     */
    @PostMapping("/mode-test")
    @ParameterCorrection(
            mode = ParameterCorrection.CorrectionMode.NORMALIZE_ONLY
    )
    public Map<String, Object> testCorrectionMode(
            @RequestParam String text,
            @RequestParam String number) {

        Map<String, Object> result = new HashMap<>();
        result.put("text", text);
        result.put("number", number);
        result.put("message", "修正模式测试完成");

        return result;
    }

    /**
     * 测试参数排除
     */
    @PostMapping("/exclusion-test")
    @ParameterCorrection(
            excludeParameters = {"excludedParam"}
    )
    public Map<String, Object> testParameterExclusion(
            @RequestParam String normalParam,
            @RequestParam String excludedParam) {

        Map<String, Object> result = new HashMap<>();
        result.put("normalParam", normalParam);
        result.put("excludedParam", excludedParam);
        result.put("message", "参数排除测试完成");

        return result;
    }
}