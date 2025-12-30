package org.zerolg.aidemo2;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AI演示项目主应用程序类
 * 这是Spring Boot应用程序的入口点，负责启动整个应用程序
 * <p>
 * 主要功能：
 * 1. 集成AI对话功能（通义千问）
 * 2. 提供知识库管理和RAG（检索增强生成）功能
 * 3. 实现参数自动纠错和验证
 * 4. 提供审计日志和性能监控
 * 5. 支持会话管理和历史记录
 */
@SpringBootApplication  // Spring Boot自动配置注解，启用自动配置、组件扫描等功能
@EnableAspectJAutoProxy // 启用AspectJ代理，支持面向切面编程（AOP）
@MapperScan(basePackages = {"org.zerolg.aidemo2.mapper", "org.zerolg.aidemo2.audit.mapper"}) // 扫描MyBatis Mapper接口
public class AiDemo2Application {

    /**
     * 应用程序主入口方法
     * 启动Spring Boot应用程序容器
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 启动Spring Boot应用程序
        SpringApplication.run(AiDemo2Application.class, args);
    }

}
