package org.zerolg.aidemo2.correction.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.correction.ParamNormalizer;
import org.zerolg.aidemo2.correction.model.CorrectionResult;
import org.zerolg.aidemo2.correction.model.ParameterContext;
import org.zerolg.aidemo2.service.MockSearchService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM Tools专用参数标准化器
 * 专门处理LLM传递的参数，进行智能修正和标准化
 */
@Component
public class LLMToolsNormalizer implements ParamNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(LLMToolsNormalizer.class);
    // LLM常见的产品名称模式
    private static final Pattern PRODUCT_PARAM_PATTERN = Pattern.compile(".*product.*|.*item.*|.*goods.*", Pattern.CASE_INSENSITIVE);
    // 仓库名称模式
    private static final Pattern WAREHOUSE_PARAM_PATTERN = Pattern.compile(".*warehouse.*|.*store.*|.*depot.*", Pattern.CASE_INSENSITIVE);
    // 数量模式
    private static final Pattern QUANTITY_PARAM_PATTERN = Pattern.compile(".*quantity.*|.*amount.*|.*count.*", Pattern.CASE_INSENSITIVE);
    // 中文数字映射 - 使用HashMap避免Map.of()限制
    private static final Map<String, String> CHINESE_NUMBERS = createChineseNumbersMap();
    private final MockSearchService searchService;

    public LLMToolsNormalizer(MockSearchService searchService) {
        this.searchService = searchService;
    }

    private static Map<String, String> createChineseNumbersMap() {
        Map<String, String> map = new HashMap<>();
        map.put("一", "1");
        map.put("二", "2");
        map.put("三", "3");
        map.put("四", "4");
        map.put("五", "5");
        map.put("六", "6");
        map.put("七", "7");
        map.put("八", "8");
        map.put("九", "9");
        map.put("十", "10");
        map.put("百", "100");
        map.put("千", "1000");
        map.put("万", "10000");
        return map;
    }

    @Override
    public CorrectionResult normalize(ParameterContext context) {
        if (!supports(context)) {
            return CorrectionResult.noCorrection(context.originalValue());
        }

        String originalValue = context.getValueAsString();
        if (originalValue == null || originalValue.trim().isEmpty()) {
            return CorrectionResult.noCorrection(originalValue);
        }

        String paramName = context.parameterName().toLowerCase();
        List<String> corrections = new ArrayList<>();
        String normalized = originalValue.trim();

        try {
            // 1. 产品名称处理
            if (PRODUCT_PARAM_PATTERN.matcher(paramName).matches()) {
                normalized = normalizeProductName(normalized, corrections);
            }

            // 2. 仓库名称处理
            else if (WAREHOUSE_PARAM_PATTERN.matcher(paramName).matches()) {
                normalized = normalizeWarehouseName(normalized, corrections);
            }

            // 3. 数量处理
            else if (QUANTITY_PARAM_PATTERN.matcher(paramName).matches()) {
                normalized = normalizeQuantity(normalized, corrections);
            }

            // 4. 通用LLM输出清理
            normalized = cleanLLMOutput(normalized, corrections);

            if (corrections.isEmpty()) {
                return CorrectionResult.noCorrection(originalValue);
            }

            double confidence = calculateLLMConfidence(originalValue, normalized, corrections);

            logger.debug("LLM参数标准化: '{}' -> '{}', 应用修正: {}", originalValue, normalized, corrections);

            return CorrectionResult.success(normalized, originalValue, corrections, confidence);

        } catch (Exception e) {
            logger.warn("LLM参数标准化失败: '{}'", originalValue, e);
            return CorrectionResult.failed(originalValue, "LLM参数标准化异常: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ParameterContext context) {
        // 支持字符串类型的LLM相关参数
        if (!String.class.equals(context.parameterType())) {
            return false;
        }

        String paramName = context.parameterName().toLowerCase();
        return PRODUCT_PARAM_PATTERN.matcher(paramName).matches() ||
                WAREHOUSE_PARAM_PATTERN.matcher(paramName).matches() ||
                QUANTITY_PARAM_PATTERN.matcher(paramName).matches() ||
                paramName.contains("name") ||
                paramName.contains("type") ||
                paramName.contains("category");
    }

    @Override
    public int getPriority() {
        return 15; // 在基础字符串标准化之后，数值标准化之前
    }

    /**
     * 标准化产品名称
     */
    private String normalizeProductName(String productName, List<String> corrections) {
        String result = productName;

        // 1. 如果已经是标准ID格式，直接返回
        if (result.startsWith("P-")) {
            return result;
        }

        // 2. 移除LLM常见的多余描述
        String cleaned = result.replaceAll("产品|商品|物品|货物", "").trim();
        if (!cleaned.equals(result)) {
            corrections.add("移除产品描述词");
            result = cleaned;
        }

        // 3. 处理品牌和型号分离
        result = normalizeBrandAndModel(result, corrections);

        // 4. 尝试通过搜索服务找到标准ID
        var searchResults = searchService.fuzzySearch(result);
        if (!searchResults.isEmpty()) {
            String bestMatch = searchResults.get(0).id();
            if (!bestMatch.equals(result)) {
                corrections.add("产品名称映射到标准ID");
                result = bestMatch;
            }
        }

        return result;
    }

    /**
     * 标准化仓库名称
     */
    private String normalizeWarehouseName(String warehouseName, List<String> corrections) {
        String result = warehouseName;

        // 1. 移除常见的仓库后缀
        String cleaned = result.replaceAll("仓库|仓|库房|中心|depot|warehouse", "").trim();
        if (!cleaned.equals(result)) {
            corrections.add("移除仓库后缀");
            result = cleaned;
        }

        // 2. 标准化地区名称
        Map<String, String> regionMapping = Map.of(
                "北京", "BEIJING",
                "上海", "SHANGHAI",
                "广州", "GUANGZHOU",
                "深圳", "SHENZHEN",
                "华东", "EAST_REGION",
                "华北", "NORTH_REGION",
                "华南", "SOUTH_REGION",
                "华西", "WEST_REGION"
        );

        String standardRegion = regionMapping.get(result);
        if (standardRegion != null) {
            corrections.add("标准化地区名称");
            result = standardRegion;
        }

        return result;
    }

    /**
     * 标准化数量
     */
    private String normalizeQuantity(String quantity, List<String> corrections) {
        String result = quantity;

        // 1. 移除单位
        String cleaned = result.replaceAll("[台个件箱批只]", "").trim();
        if (!cleaned.equals(result)) {
            corrections.add("移除数量单位");
            result = cleaned;
        }

        // 2. 处理特殊关键词
        if (result.matches(".*全部|所有|全量.*")) {
            corrections.add("识别全量关键词");
            return "ALL";
        }

        // 3. 转换中文数字
        for (Map.Entry<String, String> entry : CHINESE_NUMBERS.entrySet()) {
            if (result.contains(entry.getKey())) {
                result = result.replace(entry.getKey(), entry.getValue());
                corrections.add("转换中文数字");
            }
        }

        // 4. 处理复合中文数字（如：三十五）
        result = convertComplexChineseNumbers(result, corrections);

        return result;
    }

    /**
     * 清理LLM输出的通用问题
     */
    private String cleanLLMOutput(String input, List<String> corrections) {
        String result = input;

        // 1. 移除LLM常见的礼貌用语
        String[] politePhrases = {"请", "谢谢", "麻烦", "帮忙", "一下"};
        for (String phrase : politePhrases) {
            if (result.contains(phrase)) {
                result = result.replace(phrase, "").trim();
                corrections.add("移除礼貌用语");
            }
        }

        // 2. 移除多余的标点符号
        String cleaned = result.replaceAll("[，。！？；：''（）【】]", "").trim();
        if (!cleaned.equals(result)) {
            corrections.add("移除多余标点");
            result = cleaned;
        }

        // 3. 标准化空格
        String spaceNormalized = result.replaceAll("\\s+", " ").trim();
        if (!spaceNormalized.equals(result)) {
            corrections.add("标准化空格");
            result = spaceNormalized;
        }

        return result;
    }

    /**
     * 标准化品牌和型号
     */
    private String normalizeBrandAndModel(String input, List<String> corrections) {
        String result = input;

        // 常见品牌标准化
        Map<String, String> brandMapping = Map.of(
                "苹果", "Apple",
                "华为", "Huawei",
                "小米", "Xiaomi",
                "三星", "Samsung",
                "联想", "Lenovo"
        );

        for (Map.Entry<String, String> entry : brandMapping.entrySet()) {
            if (result.contains(entry.getKey())) {
                result = result.replace(entry.getKey(), entry.getValue());
                corrections.add("标准化品牌名称");
            }
        }

        return result;
    }

    /**
     * 转换复合中文数字
     */
    private String convertComplexChineseNumbers(String input, List<String> corrections) {
        String result = input;

        // 使用HashMap来避免Map.of()的参数限制
        Map<String, String> complexNumbers = new HashMap<>();
        complexNumbers.put("十一", "11");
        complexNumbers.put("十二", "12");
        complexNumbers.put("十三", "13");
        complexNumbers.put("十四", "14");
        complexNumbers.put("十五", "15");
        complexNumbers.put("十六", "16");
        complexNumbers.put("十七", "17");
        complexNumbers.put("十八", "18");
        complexNumbers.put("十九", "19");
        complexNumbers.put("二十", "20");
        complexNumbers.put("三十", "30");
        complexNumbers.put("四十", "40");
        complexNumbers.put("五十", "50");
        complexNumbers.put("六十", "60");
        complexNumbers.put("七十", "70");
        complexNumbers.put("八十", "80");
        complexNumbers.put("九十", "90");
        complexNumbers.put("一百", "100");
        complexNumbers.put("二百", "200");
        complexNumbers.put("三百", "300");
        complexNumbers.put("四百", "400");
        complexNumbers.put("五百", "500");
        complexNumbers.put("六百", "600");
        complexNumbers.put("七百", "700");
        complexNumbers.put("八百", "800");
        complexNumbers.put("九百", "900");
        complexNumbers.put("一千", "1000");
        complexNumbers.put("二千", "2000");
        complexNumbers.put("三千", "3000");
        complexNumbers.put("五千", "5000");
        complexNumbers.put("一万", "10000");

        for (Map.Entry<String, String> entry : complexNumbers.entrySet()) {
            if (result.contains(entry.getKey())) {
                result = result.replace(entry.getKey(), entry.getValue());
                corrections.add("转换复合中文数字");
            }
        }

        return result;
    }

    /**
     * 计算LLM参数修正置信度
     */
    private double calculateLLMConfidence(String original, String normalized, List<String> corrections) {
        double confidence = 0.8; // 基础置信度

        // 根据修正类型调整置信度
        for (String correction : corrections) {
            switch (correction) {
                case "产品名称映射到标准ID" -> confidence += 0.15;
                case "标准化地区名称", "标准化品牌名称" -> confidence += 0.1;
                case "转换中文数字", "转换复合中文数字" -> confidence += 0.05;
                case "移除产品描述词", "移除仓库后缀" -> confidence -= 0.05;
                case "识别全量关键词" -> confidence += 0.1;
                default -> confidence -= 0.02;
            }
        }

        // 基于相似度调整
        double similarity = calculateSimilarity(original.toLowerCase(), normalized.toLowerCase());
        confidence = confidence * 0.7 + similarity * 0.3;

        return Math.max(0.4, Math.min(0.95, confidence));
    }

    /**
     * 计算字符串相似度
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLength;
    }

    /**
     * 计算编辑距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]) + 1;
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }
}