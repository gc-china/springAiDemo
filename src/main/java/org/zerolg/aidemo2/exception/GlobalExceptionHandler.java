package org.zerolg.aidemo2.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.zerolg.aidemo2.common.ApiResponse;
import org.zerolg.aidemo2.common.BusinessException;
import org.zerolg.aidemo2.common.ResultCode;

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
     * 处理未捕获的系统异常
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        logger.error("系统异常: ", e);
        return ApiResponse.failed(ResultCode.FAILED.getCode(), "系统繁忙，请稍后重试: " + e.getMessage());
    }
}
