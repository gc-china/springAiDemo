// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.model;

/**
 * 幻觉验证结果模型
 *
 * 这是一个记录类（record），用于封装AI回答内容的幻觉检测结果
 *
 * 什么是AI幻觉：
 * AI幻觉是指大语言模型生成看似合理但实际上不准确或虚假的信息。
 * 这种现象在AI系统中很常见，特别是当模型缺乏相关知识或
 * 试图回答超出其训练数据范围的问题时。
 *
 * 验证的重要性：
 * 1. 准确性保证：确保AI回答的内容是可信的
 * 2. 用户信任：通过验证建立用户对AI系统的信任
 * 3. 风险控制：避免传播错误或有害信息
 * 4. 质量提升：持续改进AI回答的质量
 *
 * 验证方法：
 * - 知识库对比：将AI回答与已知的可靠知识库进行对比
 * - 事实核查：检查回答中的具体事实是否正确
 * - 逻辑一致性：验证回答的逻辑是否自洽
 * - 来源追溯：确认信息是否有可靠的来源支持
 *
 * 使用场景：
 * - 问答系统：验证AI回答的准确性
 * - 内容生成：检查生成内容的可信度
 * - 知识管理：确保知识库内容的质量
 * - 合规检查：满足特定行业的准确性要求
 *
 * @param passed 是否通过验证 (true=可信, false=存疑)
 * @param confidence 裁判的置信度 (0.0 - 1.0)
 * @param reason 判决理由 (例如: "文档中未提及粉色")
 * @param correction (可选) 建议的修正内容
 */
public record VerificationResult(
        /**
         * 验证是否通过
         *
         * true: 表示AI回答通过了幻觉检测，内容可信
         * false: 表示AI回答存在问题，内容存疑
         *
         * 判断标准：
         * - 回答内容与知识库一致
         * - 没有明显的事实错误
         * - 逻辑推理合理
         * - 有足够的证据支持
         */
        boolean passed,

        /**
         * 验证置信度
         *
         * 范围：0.0 - 1.0
         * - 1.0: 非常确信验证结果
         * - 0.8-0.9: 比较确信
         * - 0.6-0.7: 一般确信
         * - 0.4-0.5: 不太确信
         * - 0.0-0.3: 很不确信
         *
         * 影响因素：
         * - 知识库覆盖度：相关信息越完整，置信度越高
         * - 证据强度：支持证据越充分，置信度越高
         * - 一致性程度：信息越一致，置信度越高
         * - 模型确定性：验证模型越确定，置信度越高
         */
        double confidence,

        /**
         * 判决理由
         *
         * 详细说明验证结果的原因，帮助用户理解为什么得出这个结论
         *
         * 示例：
         * - "文档中明确提到产品价格为100元，与回答一致"
         * - "文档中未提及粉色选项，回答可能存在幻觉"
         * - "回答中的日期与文档记录不符"
         * - "技术规格与官方文档完全匹配"
         *
         * 作用：
         * - 透明性：让用户了解验证的依据
         * - 可解释性：提供AI决策的解释
         * - 调试支持：帮助开发者改进系统
         * - 用户教育：帮助用户理解AI的局限性
         */
        String reason,

        /**
         * 建议的修正内容（可选）
         *
         * 当验证未通过时，提供建议的正确内容或修正方案
         *
         * 使用场景：
         * - 自动纠错：系统可以自动应用修正建议
         * - 用户提示：向用户显示正确的信息
         * - 学习改进：用于训练和改进AI模型
         * - 质量控制：确保最终输出的准确性
         *
         * 示例：
         * - 原回答："产品有粉色选项"
         * - 修正建议："根据文档，产品只有红色、蓝色、绿色三种颜色选项"
         *
         * 注意：
         * - 可以为null，表示没有具体的修正建议
         * - 修正内容应该基于可靠的知识来源
         * - 应该保持与原回答相似的语言风格
         */
        String correction
) {
    // record类型自动提供：
    // - 构造函数：public VerificationResult(boolean passed, double confidence, String reason, String correction)
    // - getter方法：passed(), confidence(), reason(), correction()
    // - equals()和hashCode()方法
    // - toString()方法

    // 可以添加静态工厂方法来简化创建过程

    /**
     * 创建验证通过的结果
     *
     * @param confidence 置信度
     * @param reason     通过的理由
     * @return 验证通过的结果对象
     */
    public static VerificationResult passed(double confidence, String reason) {
        return new VerificationResult(true, confidence, reason, null);
    }

    /**
     * 创建验证失败的结果
     *
     * @param confidence 置信度
     * @param reason     失败的理由
     * @param correction 建议的修正内容
     * @return 验证失败的结果对象
     */
    public static VerificationResult failed(double confidence, String reason, String correction) {
        return new VerificationResult(false, confidence, reason, correction);
    }

    /**
     * 创建验证失败的结果（无修正建议）
     *
     * @param confidence 置信度
     * @param reason     失败的理由
     * @return 验证失败的结果对象
     */
    public static VerificationResult failed(double confidence, String reason) {
        return new VerificationResult(false, confidence, reason, null);
    }
}