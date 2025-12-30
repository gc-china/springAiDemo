// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.model;

/**
 * 单个断言分析结果
 *
 * 这是一个记录类（record），用于封装对单个断言的详细分析结果
 *
 * 什么是断言：
 * 断言是一个可以被验证为真或假的陈述。在AI回答验证中，
 * 我们将AI的回答拆解为多个独立的断言，然后逐一验证每个断言的正确性。
 *
 * 例如，AI回答"这款手机有64GB和128GB两种存储容量，价格分别是2999元和3499元"
 * 可以拆解为以下断言：
 * 1. "这款手机有64GB存储容量"
 * 2. "这款手机有128GB存储容量"
 * 3. "64GB版本价格是2999元"
 * 4. "128GB版本价格是3499元"
 *
 * 断言级别验证的优势：
 * 1. 精确定位：能够准确找出回答中哪些部分是正确的，哪些是错误的
 * 2. 细粒度控制：可以对不同的断言采用不同的处理策略
 * 3. 可解释性：为每个断言提供具体的验证依据
 * 4. 质量评估：通过断言通过率评估整体回答质量
 *
 * 支持度级别说明：
 * - FULLY_SUPPORTED: 文档中有明确完整的依据支持这个断言
 * - PARTIALLY_SUPPORTED: 文档中有部分依据，但不够完整或存在歧义
 * - UNSUPPORTED: 文档中完全没有相关信息支持这个断言
 * - CONTRADICTED: 断言与文档内容明确矛盾
 *
 * @param assertion 断言内容
 * @param supportLevel 支持度级别
 * @param evidence 支持依据（来自文档的具体内容）
 * @param sourceLocation 来源位置（如"文档第1段"）
 * @param confidence 判断置信度 (0.0-1.0)
 */
public record AssertionAnalysis(
        /**
         * 断言内容
         *
         * 这是从AI回答中提取出的单个事实陈述
         *
         * 特点：
         * - 独立性：每个断言都是独立的，可以单独验证
         * - 具体性：断言应该是具体的、可验证的事实
         * - 原子性：不应该包含多个可分离的事实
         *
         * 示例：
         * - "产品价格是299元"
         * - "支持4G网络"
         * - "电池容量为4000mAh"
         * - "有红色和蓝色两种颜色"
         */
        String assertion,

        /**
         * 支持度级别
         *
         * 表示文档对这个断言的支持程度
         *
         * 级别详解：
         * - FULLY_SUPPORTED: 文档中有明确、完整的信息支持这个断言
         *   例如：断言"价格是299元"，文档中明确写着"售价：299元"
         *
         * - PARTIALLY_SUPPORTED: 文档中有相关信息，但不够完整或存在歧义
         *   例如：断言"支持快充"，文档中只提到"充电功率18W"
         *
         * - UNSUPPORTED: 文档中完全没有相关信息
         *   例如：断言"有无线充电功能"，但文档中没有任何关于充电的信息
         *
         * - CONTRADICTED: 断言与文档内容明确矛盾
         *   例如：断言"有粉色选项"，但文档明确说"只有黑色和白色两种颜色"
         */
        AssertionSupportLevel supportLevel,

        /**
         * 支持依据
         *
         * 来自文档的具体内容，用于支持或反驳这个断言
         *
         * 作用：
         * - 证据展示：向用户展示验证的具体依据
         * - 透明度：让验证过程可追溯、可解释
         * - 质量控制：帮助评估验证结果的可靠性
         * - 调试支持：帮助开发者理解验证逻辑
         *
         * 内容要求：
         * - 准确性：必须是文档中的原始内容，不能篡改
         * - 相关性：必须与断言直接相关
         * - 完整性：包含足够的上下文信息
         * - 简洁性：避免包含无关的冗余信息
         *
         * 示例：
         * - "根据产品规格表，售价为299元"
         * - "技术参数中明确标注：电池容量4000mAh"
         * - "颜色选项：黑色、白色（未提及粉色）"
         *
         * 注意：当supportLevel为UNSUPPORTED时，evidence可能为null
         */
        String evidence,

        /**
         * 来源位置
         *
         * 指明支持依据在文档中的具体位置，便于用户查找和验证
         *
         * 位置格式示例：
         * - "文档第1段"
         * - "产品规格表"
         * - "第3章第2节"
         * - "FAQ第5条"
         * - "页面底部注释"
         *
         * 作用：
         * - 快速定位：帮助用户快速找到相关信息
         * - 验证便利：用户可以自行验证引用的准确性
         * - 审计支持：提供完整的信息追溯链
         * - 用户体验：提高信息查找的效率
         *
         * 注意：当supportLevel为UNSUPPORTED时，sourceLocation可能为null
         */
        String sourceLocation,

        /**
         * 判断置信度
         *
         * 表示对这个断言分析结果的确信程度，范围0.0-1.0
         *
         * 置信度影响因素：
         * - 证据强度：证据越明确，置信度越高
         * - 信息完整性：信息越完整，置信度越高
         * - 上下文一致性：与其他信息越一致，置信度越高
         * - 模型确定性：分析模型越确定，置信度越高
         *
         * 置信度区间：
         * - 0.9-1.0: 非常确信，证据非常明确
         * - 0.8-0.9: 比较确信，证据比较充分
         * - 0.6-0.8: 一般确信，证据基本充分
         * - 0.4-0.6: 不太确信，证据不够充分
         * - 0.0-0.4: 很不确信，证据很弱或有矛盾
         *
         * 使用场景：
         * - 结果排序：按置信度对分析结果排序
         * - 阈值过滤：只接受高置信度的结果
         * - 人工审核：低置信度的结果需要人工审核
         * - 质量评估：评估整体分析质量
         */
        double confidence
) {

    /**
     * 创建完全支持的断言分析
     *
     * 用于创建有完整文档支持的断言分析结果
     *
     * @param assertion 断言内容
     * @param evidence 支持依据
     * @param sourceLocation 来源位置
     * @param confidence 置信度
     * @return 完全支持的断言分析对象
     */
    public static AssertionAnalysis fullySupported(String assertion, String evidence, String sourceLocation, double confidence) {
        return new AssertionAnalysis(assertion, AssertionSupportLevel.FULLY_SUPPORTED, evidence, sourceLocation, confidence);
    }

    /**
     * 创建部分支持的断言分析
     *
     * 用于创建有部分文档支持的断言分析结果
     *
     * @param assertion 断言内容
     * @param evidence 支持依据
     * @param sourceLocation 来源位置
     * @param confidence 置信度
     * @return 部分支持的断言分析对象
     */
    public static AssertionAnalysis partiallySupported(String assertion, String evidence, String sourceLocation, double confidence) {
        return new AssertionAnalysis(assertion, AssertionSupportLevel.PARTIALLY_SUPPORTED, evidence, sourceLocation, confidence);
    }

    /**
     * 创建无支持的断言分析
     *
     * 用于创建没有文档支持的断言分析结果
     *
     * @param assertion 断言内容
     * @param confidence 置信度
     * @return 无支持的断言分析对象
     */
    public static AssertionAnalysis unsupported(String assertion, double confidence) {
        return new AssertionAnalysis(assertion, AssertionSupportLevel.UNSUPPORTED, null, null, confidence);
    }

    /**
     * 创建矛盾的断言分析
     *
     * 用于创建与文档内容矛盾的断言分析结果
     *
     * @param assertion 断言内容
     * @param evidence 矛盾的依据
     * @param sourceLocation 来源位置
     * @param confidence 置信度
     * @return 矛盾的断言分析对象
     */
    public static AssertionAnalysis contradicted(String assertion, String evidence, String sourceLocation, double confidence) {
        return new AssertionAnalysis(assertion, AssertionSupportLevel.CONTRADICTED, evidence, sourceLocation, confidence);
    }
}