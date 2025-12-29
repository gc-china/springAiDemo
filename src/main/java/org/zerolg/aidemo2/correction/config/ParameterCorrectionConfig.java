package org.zerolg.aidemo2.correction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 参数修正配置
 */
@Configuration
@ConfigurationProperties(prefix = "parameter.correction")
public class ParameterCorrectionConfig {

    /**
     * 是否启用参数修正
     */
    private boolean enabled = true;

    /**
     * 默认最小置信度阈值
     */
    private double defaultMinConfidence = 0.5;

    /**
     * 是否启用详细日志
     */
    private boolean verboseLogging = false;

    /**
     * 最大候选数量
     */
    private int maxCandidates = 5;

    /**
     * 是否启用性能监控
     */
    private boolean performanceMonitoring = false;

    /**
     * 修正超时时间（毫秒）
     */
    private long correctionTimeout = 5000;

    /**
     * 字符串标准化配置
     */
    private StringNormalization stringNormalization = new StringNormalization();

    /**
     * 数值标准化配置
     */
    private NumberNormalization numberNormalization = new NumberNormalization();

    /**
     * 实体解析配置
     */
    private EntityResolution entityResolution = new EntityResolution();

    /**
     * 验证配置
     */
    private Validation validation = new Validation();

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getDefaultMinConfidence() {
        return defaultMinConfidence;
    }

    public void setDefaultMinConfidence(double defaultMinConfidence) {
        this.defaultMinConfidence = defaultMinConfidence;
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    public int getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    public boolean isPerformanceMonitoring() {
        return performanceMonitoring;
    }

    public void setPerformanceMonitoring(boolean performanceMonitoring) {
        this.performanceMonitoring = performanceMonitoring;
    }

    public long getCorrectionTimeout() {
        return correctionTimeout;
    }

    public void setCorrectionTimeout(long correctionTimeout) {
        this.correctionTimeout = correctionTimeout;
    }

    public StringNormalization getStringNormalization() {
        return stringNormalization;
    }

    public void setStringNormalization(StringNormalization stringNormalization) {
        this.stringNormalization = stringNormalization;
    }

    public NumberNormalization getNumberNormalization() {
        return numberNormalization;
    }

    public void setNumberNormalization(NumberNormalization numberNormalization) {
        this.numberNormalization = numberNormalization;
    }

    public EntityResolution getEntityResolution() {
        return entityResolution;
    }

    public void setEntityResolution(EntityResolution entityResolution) {
        this.entityResolution = entityResolution;
    }

    public Validation getValidation() {
        return validation;
    }

    public void setValidation(Validation validation) {
        this.validation = validation;
    }

    /**
     * 字符串标准化配置
     */
    public static class StringNormalization {
        private boolean enabled = true;
        private boolean removeHtmlTags = true;
        private boolean normalizeWhitespace = true;
        private boolean normalizeQuotes = true;
        private boolean fixEncoding = true;

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRemoveHtmlTags() {
            return removeHtmlTags;
        }

        public void setRemoveHtmlTags(boolean removeHtmlTags) {
            this.removeHtmlTags = removeHtmlTags;
        }

        public boolean isNormalizeWhitespace() {
            return normalizeWhitespace;
        }

        public void setNormalizeWhitespace(boolean normalizeWhitespace) {
            this.normalizeWhitespace = normalizeWhitespace;
        }

        public boolean isNormalizeQuotes() {
            return normalizeQuotes;
        }

        public void setNormalizeQuotes(boolean normalizeQuotes) {
            this.normalizeQuotes = normalizeQuotes;
        }

        public boolean isFixEncoding() {
            return fixEncoding;
        }

        public void setFixEncoding(boolean fixEncoding) {
            this.fixEncoding = fixEncoding;
        }
    }

    /**
     * 数值标准化配置
     */
    public static class NumberNormalization {
        private boolean enabled = true;
        private boolean handleCurrency = true;
        private boolean handlePercentage = true;
        private boolean handleChineseNumbers = true;
        private boolean removeThousandsSeparator = true;

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isHandleCurrency() {
            return handleCurrency;
        }

        public void setHandleCurrency(boolean handleCurrency) {
            this.handleCurrency = handleCurrency;
        }

        public boolean isHandlePercentage() {
            return handlePercentage;
        }

        public void setHandlePercentage(boolean handlePercentage) {
            this.handlePercentage = handlePercentage;
        }

        public boolean isHandleChineseNumbers() {
            return handleChineseNumbers;
        }

        public void setHandleChineseNumbers(boolean handleChineseNumbers) {
            this.handleChineseNumbers = handleChineseNumbers;
        }

        public boolean isRemoveThousandsSeparator() {
            return removeThousandsSeparator;
        }

        public void setRemoveThousandsSeparator(boolean removeThousandsSeparator) {
            this.removeThousandsSeparator = removeThousandsSeparator;
        }
    }

    /**
     * 实体解析配置
     */
    public static class EntityResolution {
        private boolean enabled = true;
        private double minSimilarityThreshold = 0.6;
        private boolean enableFuzzyMatching = true;
        private int maxFuzzyCandidates = 3;

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getMinSimilarityThreshold() {
            return minSimilarityThreshold;
        }

        public void setMinSimilarityThreshold(double minSimilarityThreshold) {
            this.minSimilarityThreshold = minSimilarityThreshold;
        }

        public boolean isEnableFuzzyMatching() {
            return enableFuzzyMatching;
        }

        public void setEnableFuzzyMatching(boolean enableFuzzyMatching) {
            this.enableFuzzyMatching = enableFuzzyMatching;
        }

        public int getMaxFuzzyCandidates() {
            return maxFuzzyCandidates;
        }

        public void setMaxFuzzyCandidates(int maxFuzzyCandidates) {
            this.maxFuzzyCandidates = maxFuzzyCandidates;
        }
    }

    /**
     * 验证配置
     */
    public static class Validation {
        private boolean enabled = true;
        private boolean enableTypeValidation = true;
        private boolean enableRangeValidation = true;
        private boolean enableConstraintValidation = true;

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnableTypeValidation() {
            return enableTypeValidation;
        }

        public void setEnableTypeValidation(boolean enableTypeValidation) {
            this.enableTypeValidation = enableTypeValidation;
        }

        public boolean isEnableRangeValidation() {
            return enableRangeValidation;
        }

        public void setEnableRangeValidation(boolean enableRangeValidation) {
            this.enableRangeValidation = enableRangeValidation;
        }

        public boolean isEnableConstraintValidation() {
            return enableConstraintValidation;
        }

        public void setEnableConstraintValidation(boolean enableConstraintValidation) {
            this.enableConstraintValidation = enableConstraintValidation;
        }
    }
}