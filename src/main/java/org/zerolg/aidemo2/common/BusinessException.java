// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.common;

/**
 * 自定义业务异常类
 *
 * 这是一个运行时异常类，专门用于处理业务逻辑中的异常情况
 *
 * 设计目的：
 * 1. 业务异常标识：区分业务异常和系统异常
 * 2. 错误码管理：统一管理业务错误码和消息
 * 3. 异常传播：在业务层抛出，在控制层统一处理
 * 4. 用户友好：提供用户可理解的错误信息
 *
 * 异常分类：
 * - 系统异常：如 NullPointerException、IOException 等，通常是程序错误
 * - 业务异常：如用户不存在、余额不足等，通常是业务规则限制
 *
 * 为什么继承 RuntimeException：
 * 1. 无需强制捕获：调用方不需要强制 try-catch
 * 2. 事务回滚：Spring 默认只对 RuntimeException 进行事务回滚
 * 3. 代码简洁：避免在每个方法上声明 throws
 * 4. 业务语义：业务异常通常不需要调用方处理，而是统一处理
 *
 * 使用场景：
 * - 参数验证失败：如必填参数为空
 * - 业务规则违反：如库存不足、权限不够
 * - 数据状态异常：如订单已取消、用户已禁用
 * - 外部服务异常：如第三方 API 调用失败
 *
 * 异常处理流程：
 * 1. Service 层检测到业务异常情况
 * 2. 抛出 BusinessException 异常
 * 3. 异常向上传播到 Controller 层
 * 4. GlobalExceptionHandler 统一捕获处理
 * 5. 转换为标准的 ApiResponse 返回给前端
 *
 * 使用示例：
 *
 * // 在 Service 中抛出业务异常
 * if (user == null) {
 *     throw new BusinessException("用户不存在");
 * }
 *
 * if (balance < amount) {
 *     throw new BusinessException(ResultCode.INSUFFICIENT_BALANCE);
 * }
 *
 * // 在 GlobalExceptionHandler 中统一处理
 * @ExceptionHandler(BusinessException.class)
 * public ApiResponse<Void> handleBusinessException(BusinessException e) {
 *     return ApiResponse.failed(e.getResultCode());
 * }
 */
public class BusinessException extends RuntimeException {

    /**
     * 错误码对象
     * <p>
     * 包含错误码和错误消息的枚举对象
     * 用于标准化错误信息的管理
     */
    private final ResultCode resultCode;

    /**
     * 构造函数：使用错误码枚举创建业务异常
     *
     * 这是推荐的创建方式，使用预定义的错误码确保错误信息的一致性
     *
     * 使用示例：
     * throw new BusinessException(ResultCode.VALIDATE_FAILED);
     * throw new BusinessException(ResultCode.UNAUTHORIZED);
     *
     * @param resultCode 错误码枚举，包含错误码和错误消息
     */
    public BusinessException(ResultCode resultCode) {
        // 调用父类构造函数，设置异常消息
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    /**
     * 构造函数：使用自定义消息创建业务异常
     *
     * 用于创建临时的、不在错误码枚举中的业务异常
     * 默认使用通用的失败错误码
     *
     * 使用示例：
     * throw new BusinessException("用户名已存在");
     * throw new BusinessException("文件格式不支持");
     *
     * @param message 自定义的错误消息
     */
    public BusinessException(String message) {
        // 调用父类构造函数，设置异常消息
        super(message);
        // 使用默认的失败错误码
        this.resultCode = ResultCode.FAILED;
    }

    /**
     * 构造函数：使用错误码枚举和自定义消息创建业务异常
     *
     * 用于在使用标准错误码的同时，提供更具体的错误描述
     *
     * 使用示例：
     * throw new BusinessException(ResultCode.VALIDATE_FAILED, "用户名长度必须在3-20个字符之间");
     * throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "OpenAI API 调用超时");
     *
     * @param resultCode 错误码枚举
     * @param message 自定义的错误消息，会覆盖错误码中的默认消息
     */
    public BusinessException(ResultCode resultCode, String message) {
        // 调用父类构造函数，使用自定义消息
        super(message);
        this.resultCode = resultCode;
    }

    /**
     * 获取错误码对象
     *
     * 用于异常处理器获取标准化的错误码信息
     *
     * @return 错误码枚举对象
     */
    public ResultCode getResultCode() {
        return resultCode;
    }
}
