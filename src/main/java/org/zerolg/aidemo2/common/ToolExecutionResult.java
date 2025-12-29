package org.zerolg.aidemo2.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 工具接口统一返回结构
 */
public record ToolExecutionResult(
        String status,  // ok | ambiguous | not_found | error | pending_confirmation
        Object payload, // 具体的数据，如库存数量、候选项列表
        String explain  // 给 LLM 看的自然语言解释
) {
    private static final ObjectMapper mapper = new ObjectMapper();

    // 快捷构建方法
    public static ToolExecutionResult success(Object payload, String explain) {
        return new ToolExecutionResult("ok", payload, explain);
    }

    public static ToolExecutionResult error(String explain) {
        return new ToolExecutionResult("error", null, explain);
    }

    public static ToolExecutionResult notFound(String explain) {
        return new ToolExecutionResult("not_found", null, explain);
    }

    public static ToolExecutionResult ambiguous(Object candidates, String explain) {
        return new ToolExecutionResult("ambiguous", candidates, explain);
    }

    public static ToolExecutionResult pending(Object data, String explain) {
        return new ToolExecutionResult("pending_confirmation", data, explain);
    }

    /**
     * 需要确认的结果
     */
    public static ToolExecutionResult needsConfirmation(Object data, String explain) {
        return new ToolExecutionResult("needs_confirmation", data, explain);
    }

    // 状态检查方法
    public boolean isSuccess() {
        return "ok".equals(status);
    }

    public boolean isError() {
        return "error".equals(status);
    }

    public boolean isNotFound() {
        return "not_found".equals(status);
    }

    public boolean isAmbiguous() {
        return "ambiguous".equals(status);
    }

    public boolean isPending() {
        return "pending_confirmation".equals(status);
    }

    public boolean needsConfirmation() {
        return "needs_confirmation".equals(status);
    }

    /**
     * 获取payload数据
     */
    public Object getPayload() {
        return payload;
    }

    // 方便转换为 JSON 字符串返回给 LLM
    public String toJson() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"explain\":\"Serialization failed\"}";
        }
    }
}