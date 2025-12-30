// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.exception;

// 导入日志相关类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// 导入Spring Web相关注解
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
// 导入项目自定义类
import org.zerolg.aidemo2.common.ApiResponse;
import org.zerolg.aidemo2.common.BusinessException;
import org.zerolg.aidemo2.common.ResultCode;

// 导入Java标准库
import java.util.Map;

/**
 * 全局异常处理切面
 *
 * 这是一个全局异常处理器，用于统一处理整个应用中抛出的各种异常
 *
 * 核心功能：
 * 1. 统一异常处理：将不同类型的异常转换为统一的API响应格式
 * 2. 异常分类处理：根据异常类型提供不同的处理策略
 * 3. 日志记录：记录异常信息便于问题排查
 * 4. 用户友好：将技术异常转换为用户可理解的错误信息
 * 5. 安全保护：避免向用户暴露敏感的系统信息
 *
 * 设计原理：
 * 使用Spring的@RestControllerAdvice注解，这是一个全局的异常处理器，
 * 可以捕获所有Controller中抛出的异常，并进行统一处理。
 *
 * 异常处理层次：
 * 1. 业务异常：预期的业务逻辑异常，有明确的错误码和用户友好的消息
 * 2. 系统异常：非预期的技术异常，需要记录详细日志并返回通用错误信息
 * 3. 特殊异常：如文件上传大小超限等，需要特殊处理的异常
 *
 * 为什么需要全局异常处理：
 * - 代码简洁：避免在每个Controller方法中写try-catch
 * - 统一格式：确保所有异常都返回相同格式的响应
 * - 集中管理：异常处理逻辑集中在一个地方，便于维护
 * - 安全性：统一过滤敏感信息，避免信息泄露
 *
 * 异常处理流程：
 * 1. Controller方法执行过程中抛出异常
 * 2. Spring框架捕获异常并查找匹配的@ExceptionHandler方法
 * 3. 执行对应的异常处理方法
 * 4. 返回统一格式的ApiResponse给前端
 * 5. 前端根据响应码进行相应的用户提示
 */
@RestControllerAdvice // Spring注解：全局Controller异常处理器，自动处理所有Controller的异常
public class GlobalExceptionHandler {

    // 创建日志记录器，用于记录异常处理过程
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理自定义业务异常
     *
     * 业务异常是预期的、可控的异常，通常由业务逻辑验证失败引起
     *
     * 处理策略：
     * - 使用WARN级别记录：因为这是预期的业务异常，不是系统错误
     * - 返回业务错误码：使用异常中定义的具体错误码
     * - 保留原始消息：直接使用异常中的用户友好消息
     *
     * 典型场景：
     * - 用户输入验证失败：如必填字段为空、格式不正确
     * - 业务规则违反：如库存不足、权限不够
     * - 数据状态异常：如订单已取消、用户已禁用
     *
     * @param e 业务异常对象，包含错误码和错误消息
     * @return 标准的API错误响应
     */
    @ExceptionHandler(BusinessException.class) // Spring注解：指定处理BusinessException类型的异常
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        // 记录业务异常日志，使用WARN级别因为这是预期的异常
        logger.warn("业务异常: code={}, message={}", e.getResultCode().getCode(), e.getMessage());

        // 返回包含具体错误码和消息的API响应
        return ApiResponse.failed(e.getResultCode().getCode(), e.getMessage());
    }

    /**
     * 处理静态资源未找到异常
     *
     * 这个方法专门处理Spring Boot中的NoResourceFoundException异常，
     * 通常发生在请求不存在的静态资源或API路径时
     *
     * 特殊处理逻辑：
     * 1. 检测知识库API调用错误：识别缺少参数的API调用
     * 2. 提供详细的错误信息：包括正确的API格式和示例
     * 3. API使用指导：为开发者提供可用的API端点列表
     *
     * 为什么需要特殊处理：
     * - 开发友好：帮助开发者快速定位API调用问题
     * - 错误诊断：区分真正的404和API调用错误
     * - 用户体验：提供有用的错误信息而不是通用的404
     *
     * @param e 资源未找到异常，包含请求的资源路径
     * @return 包含详细错误信息的API响应
     */
    @ExceptionHandler(NoResourceFoundException.class) // Spring注解：处理资源未找到异常
    public ApiResponse<Map<String, Object>> handleNoResourceFoundException(NoResourceFoundException e) {
        // 获取请求的资源路径
        String resourcePath = e.getResourcePath();
        logger.warn("静态资源未找到: {}", resourcePath);

        // 特殊处理：检查是否是知识库相关的API请求缺少参数
        if (resourcePath != null && resourcePath.startsWith("api/ai/knowledge/")) {
            // 提取API操作名称
            String operation = resourcePath.substring("api/ai/knowledge/".length());

            // 检测特定的URL格式错误：如果是 preview. 或 download. 结尾，说明缺少documentId
            if (operation.equals("preview.") || operation.equals("download.")) {
                logger.error("检测到URL格式错误: {} - 缺少documentId参数", resourcePath);

                // 构建详细的错误信息，帮助开发者理解问题
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

            // 其他知识库API调用错误，提供帮助信息
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

        // 其他静态资源未找到的情况
        return ApiResponse.failed(404, "资源未找到: " + resourcePath);
    }

    /**
     * 处理未捕获的系统异常
     *
     * 这是最后的异常处理兜底机制，处理所有其他未被特定处理器捕获的异常
     *
     * 处理策略：
     * - 使用ERROR级别记录：因为这是非预期的系统异常
     * - 记录完整堆栈：便于开发人员排查问题
     * - 隐藏技术细节：向用户返回通用的错误信息
     * - 包含部分信息：在开发环境可以包含异常消息，生产环境应该隐藏
     *
     * 常见的系统异常：
     * - NullPointerException：空指针异常
     * - IllegalArgumentException：非法参数异常
     * - SQLException：数据库操作异常
     * - IOException：IO操作异常
     * - 网络连接异常、第三方服务异常等
     *
     * 安全考虑：
     * - 不向用户暴露敏感的系统信息
     * - 记录详细日志供开发人员排查
     * - 返回通用的用户友好错误信息
     *
     * @param e 系统异常对象
     * @return 通用的系统错误响应
     */
    @ExceptionHandler(Exception.class) // Spring注解：处理所有其他类型的异常
    public ApiResponse<Void> handleException(Exception e) {
        // 记录系统异常日志，使用ERROR级别并包含完整堆栈信息
        logger.error("系统异常: ", e);

        // 返回通用的系统错误响应
        // 注意：在生产环境中，不应该将异常消息直接返回给用户
        // 这里包含异常消息主要是为了开发和测试阶段的便利
        return ApiResponse.failed(ResultCode.FAILED.getCode(), "系统繁忙，请稍后重试: " + e.getMessage());
    }

    /**
     * 捕获文件过大异常
     *
     * 处理Spring Boot文件上传大小超限异常
     *
     * 异常产生原因：
     * - 用户上传的文件超过了配置的最大文件大小限制
     * - 通常在application.yml中配置：spring.servlet.multipart.max-file-size
     *
     * 特殊处理原因：
     * - 文件上传异常需要特殊的响应格式
     * - 前端文件上传组件可能需要特定的错误响应结构
     * - 提供明确的文件大小限制信息
     *
     * 返回格式说明：
     * 这里返回Map而不是ApiResponse，是因为某些前端文件上传组件
     * 可能需要特定的响应格式才能正确处理错误
     *
     * @param e 文件大小超限异常
     * @return 文件上传错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class) // Spring注解：处理文件上传大小超限异常
    public Map<String, Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        // 记录文件上传异常（这里可以添加日志记录）
        logger.warn("文件上传大小超限: {}", e.getMessage());

        // 返回前端能识别的特定格式
        // 注意：这里使用Map而不是ApiResponse，是为了兼容前端文件上传组件
        return Map.of(
                "code", 400,                                    // 错误码
                "message", "文件大小超过限制，请上传小于 50MB 的文件", // 用户友好的错误消息
                "data", null                                    // 数据为空
        );
    }
}
