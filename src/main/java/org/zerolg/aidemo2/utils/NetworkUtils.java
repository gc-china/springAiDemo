// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.utils;

// 导入日志相关类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 导入网络异常相关类
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * 网络异常处理工具类
 *
 * 这个工具类专门用于处理网络相关的异常情况，提供统一的异常识别、描述和日志记录功能
 *
 * 核心功能：
 * 1. 异常识别：判断异常是否为网络相关问题
 * 2. 友好描述：将技术异常转换为用户友好的错误描述
 * 3. 日志记录：统一的网络异常日志记录格式
 *
 * 应用场景：
 * - AI 服务调用超时处理
 * - 文档上传网络异常
 * - 数据库连接异常
 * - 外部 API 调用失败
 * - 文件下载中断处理
 *
 * 设计原则：
 * - 静态方法：工具类不需要实例化
 * - 递归检查：深度检查异常链，找到根本原因
 * - 容错处理：即使输入为 null 也能正常处理
 * - 统一标准：提供一致的异常处理逻辑
 */
public class NetworkUtils {

    // 创建日志记录器，用于记录网络异常处理过程
    private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);

    /**
     * 判断是否为网络相关异常
     *
     * 这个方法通过多种策略来识别网络异常：
     * 1. 异常类型检查：检查是否为已知的网络异常类型
     * 2. 异常消息检查：通过关键词识别网络相关错误
     * 3. 递归检查：检查异常链中的根本原因
     *
     * 支持的异常类型：
     * - InterruptedIOException: IO 操作被中断（通常是超时）
     * - SocketTimeoutException: Socket 连接超时
     * - TimeoutException: 通用超时异常
     *
     * 支持的关键词：
     * - timeout: 超时相关
     * - connection: 连接相关
     * - i/o error: IO 错误
     * - network: 网络相关
     * - interrupted: 中断相关
     *
     * @param throwable 要检查的异常对象，可以为 null
     * @return true 如果是网络异常，false 否则
     */
    public static boolean isNetworkException(Throwable throwable) {
        // 空值检查：如果异常为 null，直接返回 false
        if (throwable == null) return false;

        // 第一层检查：直接检查异常类型
        // InterruptedIOException: IO 操作被中断，通常是因为超时
        // SocketTimeoutException: Socket 连接或读取超时
        // TimeoutException: 通用的超时异常
        if (throwable instanceof InterruptedIOException ||
                throwable instanceof SocketTimeoutException ||
                throwable instanceof TimeoutException) {
            return true;
        }

        // 第二层检查：通过异常消息中的关键词判断
        String message = throwable.getMessage();
        if (message != null) {
            // 转换为小写进行不区分大小写的匹配
            String lowerMessage = message.toLowerCase();
            // 检查常见的网络异常关键词
            return lowerMessage.contains("timeout") ||      // 超时
                    lowerMessage.contains("connection") ||   // 连接问题
                    lowerMessage.contains("i/o error") ||   // IO 错误
                    lowerMessage.contains("network") ||     // 网络问题
                    lowerMessage.contains("interrupted");   // 中断
        }

        // 第三层检查：递归检查异常链中的根本原因
        // 有时候网络异常会被包装在其他异常中，需要深入检查
        return isNetworkException(throwable.getCause());
    }

    /**
     * 获取网络异常的友好描述
     *
     * 将技术性的异常信息转换为用户友好的中文描述
     * 这样可以向用户提供更清晰的错误信息，而不是显示技术性的堆栈跟踪
     *
     * 转换规则：
     * - InterruptedIOException → "网络请求被中断"
     * - SocketTimeoutException → "网络请求超时"
     * - TimeoutException → "请求超时"
     * - I/O error 消息 → "网络连接异常"
     * - 其他情况 → "网络异常: 具体错误信息"
     *
     * @param throwable 网络异常对象
     * @return 用户友好的错误描述字符串
     */
    public static String getNetworkErrorDescription(Throwable throwable) {
        // 根据异常类型返回对应的中文描述
        if (throwable instanceof InterruptedIOException) {
            // IO 操作被中断，通常是因为超时或取消操作
            return "网络请求被中断";
        } else if (throwable instanceof SocketTimeoutException) {
            // Socket 连接或读取超时
            return "网络请求超时";
        } else if (throwable instanceof TimeoutException) {
            // 通用超时异常
            return "请求超时";
        } else if (throwable.getMessage() != null && throwable.getMessage().contains("I/O error")) {
            // IO 错误，通常是网络连接问题
            return "网络连接异常";
        } else {
            // 其他网络异常，显示具体的错误信息
            return "网络异常: " + (throwable.getMessage() != null ? throwable.getMessage() : "未知错误");
        }
    }

    /**
     * 记录网络异常日志
     *
     * 提供统一的网络异常日志记录格式，根据异常类型选择合适的日志级别
     *
     * 日志级别策略：
     * - 网络异常：使用 WARN 级别，因为这通常是临时性问题
     * - 其他异常：使用 ERROR 级别，因为可能是程序错误
     *
     * 日志格式：
     * - 网络异常："{操作名称} 网络异常: {友好描述}"
     * - 其他异常："{操作名称} 发生异常" + 完整堆栈跟踪
     *
     * 为什么区分日志级别：
     * - 网络异常通常是临时性的，不需要立即处理
     * - 程序异常可能需要开发人员立即关注
     * - 便于日志监控和告警系统区分处理
     *
     * @param logger 日志记录器，通常是调用方的 logger
     * @param operation 操作名称，用于标识是哪个操作出现了异常
     * @param throwable 异常对象
     */
    public static void logNetworkException(Logger logger, String operation, Throwable throwable) {
        // 判断是否为网络异常
        if (isNetworkException(throwable)) {
            // 网络异常使用 WARN 级别，只记录友好描述，不记录堆栈跟踪
            // 因为网络异常通常是临时性的，不需要详细的调试信息
            logger.warn("{} 网络异常: {}", operation, getNetworkErrorDescription(throwable));
        } else {
            // 非网络异常使用 ERROR 级别，记录完整的堆栈跟踪
            // 因为这可能是程序错误，需要详细信息进行调试
            logger.error("{} 发生异常", operation, throwable);
        }
    }
}