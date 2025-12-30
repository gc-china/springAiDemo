// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.common;

// 导入日志上下文相关类
import org.slf4j.MDC;

/**
 * 通用 API 响应封装类
 *
 * 这是一个泛型类，用于统一封装所有 API 接口的响应格式
 *
 * 设计目的：
 * 1. 统一响应格式：所有 API 返回相同的数据结构
 * 2. 标准化处理：成功和失败都有统一的处理方式
 * 3. 便于前端处理：前端可以用统一的方式处理所有 API 响应
 * 4. 链路追踪：自动添加 TraceId 便于问题排查
 * 5. 扩展性：可以方便地添加新的响应字段
 *
 * 响应格式说明：
 * {
 *   "code": 200,                    // 响应码：200 成功，其他为各种错误码
 *   "message": "操作成功",           // 响应消息：给用户看的提示信息
 *   "data": {...},                  // 响应数据：实际的业务数据
 *   "traceId": "abc123def456"       // 追踪ID：用于日志关联和问题排查
 * }
 *
 * 泛型设计的优势：
 * - 类型安全：编译时就能确定数据类型
 * - 代码复用：一个类可以处理不同类型的数据
 * - IDE 支持：更好的代码提示和类型检查
 *
 * 使用场景：
 * - Controller 方法的返回值统一封装
 * - 异常处理器的错误响应
 * - 微服务间的接口调用响应
 * - 前后端数据交互的标准格式
 *
 * @param <T> 响应数据的类型，可以是任意类型（String、List、Map、自定义对象等）
 */
public class ApiResponse<T> {

    /**
     * 响应码
     * <p>
     * 用于标识请求的处理结果：
     * - 200: 成功
     * - 400-499: 客户端错误（参数错误、权限不足等）
     * - 500-599: 服务器错误（系统异常、服务不可用等）
     * - 10000+: 业务错误（自定义业务异常码）
     */
    private long code;

    /**
     * 响应消息
     *
     * 给用户看的提示信息：
     * - 成功时：如 "操作成功"、"数据获取成功"
     * - 失败时：如 "参数错误"、"系统异常"、"权限不足"
     *
     * 设计原则：
     * - 用户友好：使用用户能理解的语言
     * - 简洁明了：不要过于技术化
     * - 国际化：支持多语言（如果需要）
     */
    private String message;

    /**
     * 响应数据
     *
     * 实际的业务数据，类型由泛型 T 决定：
     * - 查询接口：返回查询结果（对象、列表等）
     * - 操作接口：返回操作结果（ID、状态等）
     * - 失败响应：通常为 null
     */
    private T data;

    /**
     * 追踪ID
     *
     * 用于链路追踪和问题排查：
     * - 自动从 MDC 中获取当前请求的 TraceId
     * - 前端可以在报错时提供此 ID 给技术支持
     * - 后端可以根据此 ID 快速定位相关日志
     * - 在分布式系统中用于跨服务追踪
     */
    private String traceId;

    /**
     * 无参构造函数
     *
     * 主要用于：
     * - JSON 反序列化：Jackson 等框架需要无参构造函数
     * - 框架兼容：某些框架可能需要无参构造函数
     * - 继承扩展：子类可能需要调用父类无参构造函数
     */
    protected ApiResponse() {
    }

    /**
     * 全参构造函数
     *
     * 用于创建完整的响应对象，所有静态工厂方法都会调用这个构造函数
     *
     * @param code 响应码
     * @param message 响应消息
     * @param data 响应数据
     */
    protected ApiResponse(long code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        // 自动从 MDC 中获取当前请求的 traceId
        // MDC (Mapped Diagnostic Context) 是 SLF4J 提供的线程本地存储
        this.traceId = MDC.get("traceId");
    }

    /**
     * 成功响应的静态工厂方法
     *
     * 用于创建成功的 API 响应，使用默认的成功消息
     *
     * 使用示例：
     * return ApiResponse.success(userList);
     * return ApiResponse.success("Hello World");
     * return ApiResponse.success(Collections.emptyList());
     *
     * @param data 要返回的数据
     * @param <T> 数据类型
     * @return 成功的 API 响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应的静态工厂方法（自定义消息）
     *
     * 用于创建成功的 API 响应，使用自定义的成功消息
     *
     * 使用示例：
     * return ApiResponse.success(user, "用户创建成功");
     * return ApiResponse.success(null, "删除成功");
     *
     * @param data 要返回的数据
     * @param message 自定义的成功消息
     * @param <T> 数据类型
     * @return 成功的 API 响应对象
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败响应的静态工厂方法（使用错误码枚举）
     *
     * 用于创建失败的 API 响应，使用预定义的错误码
     *
     * 使用示例：
     * return ApiResponse.failed(ResultCode.VALIDATE_FAILED);
     * return ApiResponse.failed(ResultCode.UNAUTHORIZED);
     *
     * @param errorCode 错误码枚举
     * @param <T> 数据类型
     * @return 失败的 API 响应对象
     */
    public static <T> ApiResponse<T> failed(ResultCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 失败响应的静态工厂方法（自定义消息）
     *
     * 用于创建失败的 API 响应，使用自定义的错误消息
     *
     * 使用示例：
     * return ApiResponse.failed("用户名已存在");
     * return ApiResponse.failed("文件上传失败");
     *
     * @param message 自定义的错误消息
     * @param <T> 数据类型
     * @return 失败的 API 响应对象
     */
    public static <T> ApiResponse<T> failed(String message) {
        return new ApiResponse<>(ResultCode.FAILED.getCode(), message, null);
    }

    /**
     * 失败响应的静态工厂方法（自定义错误码和消息）
     *
     * 用于创建失败的 API 响应，完全自定义错误码和消息
     *
     * 使用示例：
     * return ApiResponse.failed(10001, "AI 服务暂时不可用");
     * return ApiResponse.failed(10002, "文档解析失败");
     *
     * @param code 自定义错误码
     * @param message 自定义错误消息
     * @param <T> 数据类型
     * @return 失败的 API 响应对象
     */
    public static <T> ApiResponse<T> failed(long code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    // ==================== Getter 和 Setter 方法 ====================

    /**
     * 获取响应码
     * @return 响应码
     */
    public long getCode() {
        return code;
    }

    /**
     * 设置响应码
     * @param code 响应码
     */
    public void setCode(long code) {
        this.code = code;
    }

    /**
     * 获取响应消息
     * @return 响应消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置响应消息
     * @param message 响应消息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取响应数据
     * @return 响应数据
     */
    public T getData() {
        return data;
    }

    /**
     * 设置响应数据
     * @param data 响应数据
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
     * 获取追踪ID
     * @return 追踪ID
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * 设置追踪ID
     * @param traceId 追踪ID
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
