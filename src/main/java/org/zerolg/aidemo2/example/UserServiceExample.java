package org.zerolg.aidemo2.example;

import jakarta.validation.constraints.Email;
import org.springframework.web.bind.annotation.*;
import org.zerolg.aidemo2.correction.annotation.ParameterCorrection;
import org.zerolg.aidemo2.correction.annotation.PositiveNumber;

import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务示例 - 展示参数修正系统的实际使用
 */
@RestController
@RequestMapping("/api/users")
public class UserServiceExample {

    /**
     * 1. 基本使用 - 只需添加 @ParameterCorrection 注解
     */
    @PostMapping("/basic")
    @ParameterCorrection  // 🔥 关键：添加这个注解启用参数修正
    public Map<String, Object> createUserBasic(
            @RequestParam String name,        // 自动清理空白字符、HTML标签等
            @RequestParam Integer age,        // 自动转换中文数字 "二十五" → 25
            @RequestParam Boolean isActive) { // 自动解析 "是"/"否"/"启用"/"禁用" 等

        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("age", age);
        result.put("isActive", isActive);
        result.put("message", "用户创建成功");

        return result;
    }

    /**
     * 2. 高级使用 - 配合验证注解
     */
    @PostMapping("/advanced")
    @ParameterCorrection(
            failOnError = false,        // 修正失败时不抛异常
            autoConfirm = true,         // 自动确认歧义结果
            minConfidence = 0.7         // 只应用置信度>0.7的修正
    )
    public Map<String, Object> createUserAdvanced(
            @RequestParam @Size(min = 2, max = 50) String name,  // 长度验证
            @RequestParam @Min(18) @Max(120) Integer age,        // 范围验证
            @RequestParam @Email String email,                   // 邮箱格式验证
            @RequestParam @PositiveNumber Double salary,         // 自定义正数验证
            @RequestParam @Past LocalDate birthDate) {           // 过去日期验证

        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("age", age);
        result.put("email", email);
        result.put("salary", salary);
        result.put("birthDate", birthDate);
        result.put("message", "高级用户创建成功");

        return result;
    }

    /**
     * 3. 选择性修正 - 只修正特定参数
     */
    @PostMapping("/selective")
    @ParameterCorrection(
            includeParameters = {"name", "description"}, // 只修正这些参数
            excludeParameters = {"id"}                   // 排除这些参数
    )
    public Map<String, Object> updateUserSelective(
            @RequestParam String id,          // 不会被修正
            @RequestParam String name,        // 会被修正
            @RequestParam String description, // 会被修正
            @RequestParam Integer version) {  // 不会被修正（不在includeParameters中）

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("name", name);
        result.put("description", description);
        result.put("version", version);

        return result;
    }

    /**
     * 4. 不同修正模式
     */
    @PostMapping("/normalize-only")
    @ParameterCorrection(
            mode = ParameterCorrection.CorrectionMode.NORMALIZE_ONLY // 只标准化，不验证
    )
    public Map<String, Object> normalizeOnly(
            @RequestParam String text,
            @RequestParam String number) {

        Map<String, Object> result = new HashMap<>();
        result.put("text", text);
        result.put("number", number);
        result.put("message", "仅标准化处理完成");

        return result;
    }
}