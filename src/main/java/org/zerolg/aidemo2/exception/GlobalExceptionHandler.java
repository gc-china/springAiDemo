package org.zerolg.aidemo2.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.zerolg.aidemo2.common.ApiResponse;
import org.zerolg.aidemo2.common.BusinessException;
import org.zerolg.aidemo2.common.ResultCode;

import java.util.Map;

/**
 * 全局异常处理切面
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: code={}, message={}", e.getResultCode().getCode(), e.getMessage());
        return ApiResponse.failed(e.getResultCode().getCode(), e.getMessage());
    }

    /**
     * 处理静态资源未找到异常
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Map<String, Object>> handleNoResourceFoundException(NoResourceFoundException e) {
        String resourcePath = e.getResourcePath();
        logger.warn("静态资源未找到: {}", resourcePath);

        // 检查是否是知识库相关的API请求缺少参数
        if (resourcePath != null && resourcePath.startsWith("api/ai/knowledge/")) {
            String operation = resourcePath.substring("api/ai/knowledge/".length());

            // 特殊处理：如果是 preview. 或 download. 结尾，说明缺少documentId
            if (operation.equals("preview.") || operation.equals("download.")) {
                logger.error("检测到URL格式错误: {} - 缺少documentId参数", resourcePath);
                Map<String, Object> errorInfo = Map.of(
                        "error", "URL格式错误",
                        "message", "缺少必需的documentId参数",
                        "requestedPath", resourcePath,
                        "correctFormat", operation.equals("preview.") ?
                                "/api/ai/knowledge/preview/{documentId}" :
                                "/api/ai/knowledge/download/{documentId}",
                        "example", operation.equals("preview.") ?
                                "/api/ai/knowledge/preview/123e4567-e89b-12d3-a456-426614174000" :
                                "/api/ai/knowledge/download/123e4567-e89b-12d3-a456-426614174000"
                );
                return ApiResponse.failed(400, "URL格式错误，缺少documentId参数");
            }

            Map<String, Object> helpInfo = Map.of(
                    "error", "API路径不完整",
                    "message", "请检查API调用是否包含必需的参数",
                    "requestedPath", resourcePath,
                    "suggestion", switch (operation) {
                        case "citation" -> "使用: GET /api/ai/knowledge/citation/{documentId}";
                        case "preview" -> "使用: GET /api/ai/knowledge/preview/{documentId}";
                        case "download" -> "使用: GET /api/ai/knowledge/download/{documentId}";
                        default -> "请查看API文档了解正确的调用方式";
                    },
                    "availableEndpoints", Map.of(
                            "获取文档列表", "GET /api/ai/knowledge/documents",
                            "获取引用信息", "GET /api/ai/knowledge/citation/{documentId}",
                            "预览文档", "GET /api/ai/knowledge/preview/{documentId}",
                            "下载文档", "GET /api/ai/knowledge/download/{documentId}"
                    )
            );

            return ApiResponse.failed(404, "API调用错误");
        }

        // 其他静态资源未找到
        return ApiResponse.failed(404, "资源未找到: " + resourcePath);
    }

    /**
     * 处理未捕获的系统异常
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        logger.error("系统异常: ", e);
        return ApiResponse.failed(ResultCode.FAILED.getCode(), "系统繁忙，请稍后重试: " + e.getMessage());
    }

    /**
     * 捕获文件过大异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Map<String, Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        // 这里返回前端能识别的格式
        return Map.of(
                "code", 400,
                "message", "文件大小超过限制，请上传小于 50MB 的文件",
                "data", null
        );
    }
}
