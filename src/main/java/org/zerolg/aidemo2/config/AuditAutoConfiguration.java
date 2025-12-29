package org.zerolg.aidemo2.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 审计系统自动配置
 * 当audit.enabled=false时，完全禁用审计相关组件
 */
@Configuration
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = false)
public class AuditAutoConfiguration {
    // 这个配置类只有在audit.enabled=true时才会被加载
    // 所有审计相关的组件都应该依赖这个配置类
}