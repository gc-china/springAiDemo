package org.zerolg.aidemo2.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * 统一日志切面
 * 负责记录请求日志、耗时和 TraceId 管理
 */
@Aspect
@Component
public class WebLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(WebLogAspect.class);
    private static final String TRACE_ID = "traceId";

    @Pointcut("execution(public * org.zerolg.aidemo2.controller..*.*(..))")
    public void webLog() {
    }

    @Around("webLog()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 1. 设置 TraceId
        if (MDC.get(TRACE_ID) == null) {
            MDC.put(TRACE_ID, UUID.randomUUID().toString().replace("-", ""));
        }

        // 2. 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        
        String url = request.getRequestURL().toString();
        String method = request.getMethod();
        String ip = request.getRemoteAddr();
        String classMethod = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        
        // 3. 记录请求日志
        logger.info("========================================== Start ==========================================");
        logger.info("URL          : {}", url);
        logger.info("HTTP Method  : {}", method);
        logger.info("Class Method : {}", classMethod);
        logger.info("IP           : {}", ip);
        
        // 尝试打印请求参数 (排除 Request/Response/MultipartFile 等大对象)
        try {
            Object[] args = joinPoint.getArgs();
            for (Object arg : args) {
                if (!(arg instanceof HttpServletRequest) && 
                    !(arg instanceof HttpServletResponse) && 
                    !(arg instanceof MultipartFile)) {
                    logger.info("Request Args : {}", arg);
                }
            }
        } catch (Exception e) {
            // ignore args log error
        }

        Object result = null;
        try {
            // 4. 执行目标方法
            result = joinPoint.proceed();
        } finally {
            // 5. 记录响应耗时
            long costTime = System.currentTimeMillis() - startTime;
            logger.info("Time Cost    : {} ms", costTime);
            logger.info("=========================================== End ===========================================");
            
            // 清理 MDC
            MDC.remove(TRACE_ID);
        }
        
        return result;
    }
}
