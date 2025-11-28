package org.zerolg.aidemo2.common;

/**
 * API 响应码枚举
 */
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(404, "参数检验失败"),
    UNAUTHORIZED(401, "暂无登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),
    
    // 业务错误码 (10000 - 19999)
    AI_SERVICE_ERROR(10001, "AI 服务调用失败"),
    RAG_SEARCH_ERROR(10002, "RAG 检索失败"),
    TOOL_EXECUTION_ERROR(10003, "工具执行异常");

    private final long code;
    private final String message;

    ResultCode(long code, String message) {
        this.code = code;
        this.message = message;
    }

    public long getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
