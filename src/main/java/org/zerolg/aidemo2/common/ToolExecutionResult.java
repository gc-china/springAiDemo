package org.zerolg.aidemo2.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 工具执行结果统一返回结构
 *
 * 这是AI工具调用的标准返回格式，用于统一不同工具的返回结果。
 *
 * 设计目标：
 * 1. 标准化：所有工具都使用相同的返回格式
 * 2. 可扩展：支持不同类型的执行状态和数据
 * 3. AI友好：提供自然语言解释，帮助AI理解结果
 * 4. 类型安全：使用record确保数据不可变性
 *
 * 使用场景：
 * - 库存查询工具：返回库存数量和状态说明
 * - 参数纠错工具：返回纠错结果和候选选项
 * - 业务操作工具：返回操作结果和详细说明
 *
 * 与AI的交互：
 * - AI通过status字段判断执行是否成功
 * - AI通过payload获取具体的业务数据
 * - AI通过explain理解结果的含义和后续操作建议
 *
 * @author zerolg
 */
public record ToolExecutionResult(
        /**
         * 执行状态
         *
         * 标准状态值：
         * - "ok": 执行成功，有明确结果
         * - "error": 执行失败，出现异常或错误
         * - "not_found": 未找到相关数据或资源
         * - "ambiguous": 结果存在歧义，需要进一步澄清
         * - "pending_confirmation": 等待用户确认
         * - "needs_confirmation": 需要用户确认参数或操作
         *
         * 为什么使用字符串而不是枚举：
         * - 更容易扩展新的状态类型
         * - JSON序列化更简单
         * - 与前端交互更直观
         */
        String status,

        /**
         * 数据载荷
         *
         * 根据不同的工具和状态，包含不同类型的数据：
         * - 成功时：业务数据（如库存数量、产品信息等）
         * - 歧义时：候选选项列表
         * - 确认时：待确认的参数或操作信息
         * - 错误时：通常为null
         *
         * 使用Object类型的原因：
         * - 支持不同类型的数据结构
         * - 可以是基本类型、集合、自定义对象等
         * - Jackson可以自动处理序列化
         */
        Object payload,

        /**
         * 自然语言解释
         *
         * 这是给AI看的结果说明，帮助AI理解：
         * - 操作是否成功
         * - 结果的具体含义
         * - 下一步应该如何处理
         * - 用户需要了解的重要信息
         *
         * 示例：
         * - "查询成功，iPhone 15当前库存为150台"
         * - "产品名称存在歧义，请选择具体型号"
         * - "库存不足，无法完成调拨操作"
         */
        String explain
) {
    // Jackson对象映射器，用于JSON序列化
    private static final ObjectMapper mapper = new ObjectMapper();

    // ==================== 快捷构建方法 ====================
    // 这些静态方法提供了创建不同状态结果的便捷方式

    /**
     * 创建成功结果
     *
     * @param payload 成功时返回的数据
     * @param explain 成功说明
     * @return 成功状态的结果对象
     */
    public static ToolExecutionResult success(Object payload, String explain) {
        return new ToolExecutionResult("ok", payload, explain);
    }

    /**
     * 创建错误结果
     *
     * @param explain 错误说明
     * @return 错误状态的结果对象
     */
    public static ToolExecutionResult error(String explain) {
        return new ToolExecutionResult("error", null, explain);
    }

    /**
     * 创建未找到结果
     *
     * @param explain 未找到的说明
     * @return 未找到状态的结果对象
     */
    public static ToolExecutionResult notFound(String explain) {
        return new ToolExecutionResult("not_found", null, explain);
    }

    /**
     * 创建歧义结果
     *
     * @param candidates 候选选项列表
     * @param explain    歧义说明
     * @return 歧义状态的结果对象
     */
    public static ToolExecutionResult ambiguous(Object candidates, String explain) {
        return new ToolExecutionResult("ambiguous", candidates, explain);
    }

    /**
     * 创建等待确认结果
     *
     * @param data    待确认的数据
     * @param explain 确认说明
     * @return 等待确认状态的结果对象
     */
    public static ToolExecutionResult pending(Object data, String explain) {
        return new ToolExecutionResult("pending_confirmation", data, explain);
    }

    /**
     * 创建需要确认结果
     *
     * @param data 需要确认的数据
     * @param explain 确认说明
     * @return 需要确认状态的结果对象
     */
    public static ToolExecutionResult needsConfirmation(Object data, String explain) {
        return new ToolExecutionResult("needs_confirmation", data, explain);
    }

    // ==================== 状态检查方法 ====================
    // 这些方法提供了便捷的状态判断功能

    /**
     * 检查是否执行成功
     * @return true如果状态为"ok"
     */
    public boolean isSuccess() {
        return "ok".equals(status);
    }

    /**
     * 检查是否执行失败
     * @return true如果状态为"error"
     */
    public boolean isError() {
        return "error".equals(status);
    }

    /**
     * 检查是否未找到
     * @return true如果状态为"not_found"
     */
    public boolean isNotFound() {
        return "not_found".equals(status);
    }

    /**
     * 检查是否存在歧义
     * @return true如果状态为"ambiguous"
     */
    public boolean isAmbiguous() {
        return "ambiguous".equals(status);
    }

    /**
     * 检查是否等待确认
     * @return true如果状态为"pending_confirmation"
     */
    public boolean isPending() {
        return "pending_confirmation".equals(status);
    }

    /**
     * 检查是否需要确认
     * @return true如果状态为"needs_confirmation"
     */
    public boolean needsConfirmation() {
        return "needs_confirmation".equals(status);
    }

    /**
     * 获取数据载荷
     *
     * 这是一个便捷方法，与直接访问payload字段等效
     *
     * @return 数据载荷对象
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * 转换为JSON字符串
     *
     * 这是工具返回给AI的最终格式。AI会解析这个JSON字符串
     * 来理解工具的执行结果。
     *
     * 为什么返回JSON字符串而不是对象：
     * - Spring AI的工具接口要求返回String类型
     * - JSON格式便于AI解析和理解
     * - 支持复杂的数据结构传递
     *
     * 错误处理：
     * - 如果序列化失败，返回标准错误格式
     * - 确保AI总能收到有效的响应
     *
     * @return JSON格式的结果字符串
     */
    public String toJson() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            // 序列化失败时返回标准错误格式
            return "{\"status\":\"error\",\"explain\":\"Serialization failed\"}";
        }
    }
}