// src/main/java/org/zerolg/aidemo2/tool/UserService.java

package org.zerolg.aidemo2.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.Locale;
import java.util.function.Function;

@Configuration
public class UserService {

    /**
     * @Description: 根据用户ID查询用户的姓名、部门和联系方式。
     * 用于回答用户关于“某某人信息”或“查询联系方式”的问题。
     */
    @Bean
    @Description("根据用户ID查询用户的姓名、部门和联系方式。")
    public Function<String, String> getUserInfo() {
        return (userId) -> {
            System.out.println(">>> 🔧 工具被调用: 正在查询用户 ID: [" + userId + "] 的信息...");

            // 模拟数据库查询
            return switch (userId.toLowerCase(Locale.ROOT)) {
                case "u101" -> "用户姓名：张三，部门：市场部，联系电话：138xxxx1234";
                case "u102" -> "用户姓名：李四，部门：研发部，联系电话：139xxxx5678";
                default -> "未找到 ID 为 " + userId + " 的用户信息。";
            };
        };
    }
}
