package org.zerolg.aidemo2.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * 网络异常处理工具类
 */
public class NetworkUtils {

    private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);

    /**
     * 判断是否为网络相关异常
     */
    public static boolean isNetworkException(Throwable throwable) {
        if (throwable == null) return false;

        // 检查异常类型
        if (throwable instanceof InterruptedIOException ||
                throwable instanceof SocketTimeoutException ||
                throwable instanceof TimeoutException) {
            return true;
        }

        // 检查异常消息
        String message = throwable.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("timeout") ||
                    lowerMessage.contains("connection") ||
                    lowerMessage.contains("i/o error") ||
                    lowerMessage.contains("network") ||
                    lowerMessage.contains("interrupted");
        }

        // 递归检查原因异常
        return isNetworkException(throwable.getCause());
    }

    /**
     * 获取网络异常的友好描述
     */
    public static String getNetworkErrorDescription(Throwable throwable) {
        if (throwable instanceof InterruptedIOException) {
            return "网络请求被中断";
        } else if (throwable instanceof SocketTimeoutException) {
            return "网络请求超时";
        } else if (throwable instanceof TimeoutException) {
            return "请求超时";
        } else if (throwable.getMessage() != null && throwable.getMessage().contains("I/O error")) {
            return "网络连接异常";
        } else {
            return "网络异常: " + (throwable.getMessage() != null ? throwable.getMessage() : "未知错误");
        }
    }

    /**
     * 记录网络异常日志
     */
    public static void logNetworkException(Logger logger, String operation, Throwable throwable) {
        if (isNetworkException(throwable)) {
            logger.warn("{} 网络异常: {}", operation, getNetworkErrorDescription(throwable));
        } else {
            logger.error("{} 发生异常", operation, throwable);
        }
    }
}