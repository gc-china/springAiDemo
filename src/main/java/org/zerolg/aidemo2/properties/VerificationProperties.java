package org.zerolg.aidemo2.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.model.UnsupportedHandlingStrategy;

/**
 * 验证服务配置属性
 */
@Component
@ConfigurationProperties(prefix = "ai.verification")
public class VerificationProperties {

    /**
     * 是否启用详细验证（断言级别分析）
     */
    private boolean enableDetailedVerification = true;

    /**
     * 无支持内容处理配置
     */
    private UnsupportedHandling unsupportedHandling = new UnsupportedHandling();

    /**
     * 验证超时时间（秒）
     */
    private int timeoutSeconds = 12;

    /**
     * 断言提取超时时间（秒）
     */
    private int assertionExtractionTimeoutSeconds = 8;

    // Getters and Setters
    public boolean isEnableDetailedVerification() {
        return enableDetailedVerification;
    }

    public void setEnableDetailedVerification(boolean enableDetailedVerification) {
        this.enableDetailedVerification = enableDetailedVerification;
    }

    public UnsupportedHandling getUnsupportedHandling() {
        return unsupportedHandling;
    }

    public void setUnsupportedHandling(UnsupportedHandling unsupportedHandling) {
        this.unsupportedHandling = unsupportedHandling;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getAssertionExtractionTimeoutSeconds() {
        return assertionExtractionTimeoutSeconds;
    }

    public void setAssertionExtractionTimeoutSeconds(int assertionExtractionTimeoutSeconds) {
        this.assertionExtractionTimeoutSeconds = assertionExtractionTimeoutSeconds;
    }

    public static class UnsupportedHandling {
        /**
         * 无支持内容比例阈值（超过此比例触发处理）
         */
        private double threshold = 0.3;

        /**
         * 处理策略
         */
        private UnsupportedHandlingStrategy strategy = UnsupportedHandlingStrategy.MARK_WARNING;

        /**
         * 是否自动重新生成
         */
        private boolean autoRegenerate = false;

        /**
         * 是否显示详细分析
         */
        private boolean showDetails = true;

        /**
         * 最大重新生成次数
         */
        private int maxRegenerationAttempts = 2;

        // Getters and Setters
        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }

        public UnsupportedHandlingStrategy getStrategy() {
            return strategy;
        }

        public void setStrategy(UnsupportedHandlingStrategy strategy) {
            this.strategy = strategy;
        }

        public boolean isAutoRegenerate() {
            return autoRegenerate;
        }

        public void setAutoRegenerate(boolean autoRegenerate) {
            this.autoRegenerate = autoRegenerate;
        }

        public boolean isShowDetails() {
            return showDetails;
        }

        public void setShowDetails(boolean showDetails) {
            this.showDetails = showDetails;
        }

        public int getMaxRegenerationAttempts() {
            return maxRegenerationAttempts;
        }

        public void setMaxRegenerationAttempts(int maxRegenerationAttempts) {
            this.maxRegenerationAttempts = maxRegenerationAttempts;
        }
    }
}