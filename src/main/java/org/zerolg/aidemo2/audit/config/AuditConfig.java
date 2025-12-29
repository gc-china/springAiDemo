package org.zerolg.aidemo2.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * 审计系统配置
 */
@Configuration
@EnableAsync
@EnableAspectJAutoProxy
@ConfigurationProperties(prefix = "audit")
public class AuditConfig {

    private boolean enabled = true;
    private String detailLevel = "standard"; // minimal, standard, detailed
    private Duration retentionPeriod = Duration.ofDays(30);
    private boolean encryptSensitiveData = true;
    private int asyncPoolSize = 10;

    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncPoolSize);
        executor.setMaxPoolSize(asyncPoolSize * 2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        executor.initialize();
        return executor;
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDetailLevel() {
        return detailLevel;
    }

    public void setDetailLevel(String detailLevel) {
        this.detailLevel = detailLevel;
    }

    public Duration getRetentionPeriod() {
        return retentionPeriod;
    }

    public void setRetentionPeriod(Duration retentionPeriod) {
        this.retentionPeriod = retentionPeriod;
    }

    public boolean isEncryptSensitiveData() {
        return encryptSensitiveData;
    }

    public void setEncryptSensitiveData(boolean encryptSensitiveData) {
        this.encryptSensitiveData = encryptSensitiveData;
    }

    public int getAsyncPoolSize() {
        return asyncPoolSize;
    }

    public void setAsyncPoolSize(int asyncPoolSize) {
        this.asyncPoolSize = asyncPoolSize;
    }
}