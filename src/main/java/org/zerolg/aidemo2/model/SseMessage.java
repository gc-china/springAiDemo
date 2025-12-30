package org.zerolg.aidemo2.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * SSE消息统一格式
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseMessage(
        String type,        // thinking/content/tool/error/final/progress/ambiguous
        String stage,       // 当前阶段：retrieval/reasoning/tool_call/verification
        String delta,       // 增量内容
        Integer seq,        // 序列号
        Map<String, Object> meta  // 元数据
) {

    public static SseMessage thinking(String stage, String content, Integer seq) {
        return new SseMessage("thinking", stage, content, seq, null);
    }

    public static SseMessage content(String delta, Integer seq) {
        return new SseMessage("content", null, delta, seq, null);
    }

    public static SseMessage tool(String toolName, Map<String, Object> params, Integer seq) {
        return new SseMessage("tool", "tool_call", null, seq, Map.of(
                "toolName", toolName,
                "params", params
        ));
    }

    public static SseMessage toolResult(String toolName, Object result, Integer seq) {
        return new SseMessage("tool", "tool_result", null, seq, Map.of(
                "toolName", toolName,
                "result", result
        ));
    }

    public static SseMessage error(String message, Integer seq) {
        return new SseMessage("error", null, message, seq, null);
    }

    public static SseMessage finalMessage(Map<String, Object> meta) {
        return new SseMessage("final", null, null, null, meta);
    }

    public static SseMessage progress(String stage, int percent, String message) {
        return new SseMessage("progress", stage, message, null, Map.of(
                "percent", percent
        ));
    }

    public static SseMessage ambiguous(String toolName, Object candidates, String message, Integer seq) {
        return new SseMessage("ambiguous", "tool_call", message, seq, Map.of(
                "toolName", toolName,
                "candidates", candidates
        ));
    }

    public static SseMessage citations(Object citations, Integer seq) {
        return new SseMessage("citations", null, null, seq, Map.of(
                "citations", citations
        ));
    }

    public static SseMessage verification(Object result, Integer seq) {
        return new SseMessage("verification", null, null, seq, Map.of(
                "result", result
        ));
    }
}
