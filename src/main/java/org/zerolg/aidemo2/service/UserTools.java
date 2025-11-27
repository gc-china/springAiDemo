package org.zerolg.aidemo2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.Locale;
import java.util.function.Function;

@Configuration
public class UserTools {
    
    private static final Logger logger = LoggerFactory.getLogger(UserTools.class);

    /**
     * 查询用户信息
     * 
     * 参数类型安全保障:
     * 1. Function<String, String> 明确定义了输入输出类型
     * 2. @Description 告诉 AI 参数格式要求
     * 3. 添加了参数验证和异常处理
     */
    @Bean
    @Description("根据用户ID查询用户的姓名、部门和联系方式。参数:用户ID(字符串,例如:u101、u102)")
    public Function<String, String> getUserInfo() {
        return (userId) -> {
            try {
                // 🛡️ 参数验证
                if (userId == null || userId.isBlank()) {
                    logger.warn("收到无效的用户ID: null 或空字符串");
                    return "错误:用户ID不能为空";
                }
                
                // 🛡️ 长度和格式验证
                if (userId.length() > 50) {
                    logger.warn("用户ID过长: {}", userId);
                    return "错误:用户ID格式不正确";
                }
                
                String normalized = userId.trim().toLowerCase(Locale.ROOT);
                logger.info("🔧 工具被调用: 查询用户 [{}] 的信息", normalized);

                // 业务逻辑
                return switch (normalized) {
                    case "u101" -> "用户姓名：张三，部门：市场部，联系电话：138xxxx1234";
                    case "u102" -> "用户姓名：李四，部门：研发部，联系电话：139xxxx5678";
                    default -> {
                        logger.info("未找到用户: {}", normalized);
                        yield "未找到 ID 为 " + userId + " 的用户信息。";
                    }
                };
                
            } catch (Exception e) {
                logger.error("查询用户信息时发生异常", e);
                return "系统错误:查询用户信息失败";
            }
        };
    }
}
