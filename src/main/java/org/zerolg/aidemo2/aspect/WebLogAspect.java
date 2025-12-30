// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.aspect;

// 导入 Java 标准库
import java.util.UUID;

// 导入 AspectJ 相关类（AOP 切面编程）
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
// 导入日志相关类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
// 导入 Spring 相关类
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

// 导入 Servlet 相关类
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 统一日志切面
 *
 * 这是一个 AOP (面向切面编程) 组件，用于统一处理 Web 请求的日志记录
 *
 * 核心功能：
 * 1. 请求日志记录：自动记录所有 Controller 方法的请求信息
 * 2. TraceId 管理：为每个请求生成唯一的追踪标识
 * 3. 性能监控：记录每个请求的执行耗时
 * 4. 参数记录：记录请求参数（排除敏感和大对象）
 * 5. 统一格式：提供一致的日志输出格式
 *
 * AOP 概念说明：
 * AOP (Aspect-Oriented Programming) 面向切面编程是一种编程范式，
 * 用于将横切关注点（如日志、安全、事务等）从业务逻辑中分离出来
 *
 * 核心概念：
 * - Aspect (切面): 横切关注点的模块化，如日志记录
 * - Join Point (连接点): 程序执行过程中的特定点，如方法调用
 * - Pointcut (切点): 定义在哪些连接点应用切面逻辑
 * - Advice (通知): 在连接点执行的代码，如 @Around、@Before、@After
 *
 * TraceId 的作用：
 * - 请求追踪：在分布式系统中追踪单个请求的完整链路
 * - 日志关联：将同一请求的所有日志关联起来
 * - 问题排查：快速定位特定请求的所有相关日志
 * - 性能分析：分析单个请求的完整执行过程
 *
 * MDC (Mapped Diagnostic Context) 说明：
 * MDC 是 SLF4J 提供的一种机制，用于在日志中添加上下文信息
 * - 线程安全：每个线程有独立的 MDC 上下文
 * - 自动传播：MDC 中的信息会自动添加到日志输出中
 * - 便于过滤：可以根据 MDC 信息过滤和搜索日志
 *
 * 应用场景：
 * - 接口调用监控：监控所有 API 的调用情况
 * - 性能分析：识别慢接口和性能瓶颈
 * - 问题排查：快速定位接口调用问题
 * - 审计日志：记录用户操作和系统行为
 * - 链路追踪：在微服务架构中追踪请求链路
 */
@Aspect // AspectJ 注解：标记这是一个切面类
@Component // Spring 注解：标记这是一个 Spring 组件，会被自动扫描和注册
public class WebLogAspect {

    // 创建日志记录器，用于输出切面处理的日志信息
    private static final Logger logger = LoggerFactory.getLogger(WebLogAspect.class);

    // TraceId 在 MDC 中的键名，用于在日志中标识请求
    private static final String TRACE_ID = "traceId";

    /**
     * 定义切点：匹配所有 Controller 层的公共方法
     * <p>
     * Pointcut 表达式说明：
     * execution(public * org.zerolg.aidemo2.controller..*.*(..))
     * <p>
     * 表达式分解：
     * - execution: 方法执行连接点
     * - public: 访问修饰符（public 方法）
     * - *: 返回类型（任意类型）
     * - org.zerolg.aidemo2.controller: 包名
     * - ..: 包及其子包
     * - *: 类名（任意类名）
     * - .*: 方法名（任意方法名）
     * - (..): 参数列表（任意参数）
     * <p>
     * 这个表达式会匹配 controller 包及其子包下所有类的所有公共方法
     * <p>
     * 为什么只拦截 Controller：
     * - Controller 是 Web 层的入口，代表用户请求
     * - 避免过度拦截：不拦截 Service、Repository 等内部调用
     * - 性能考虑：减少不必要的日志输出
     * - 清晰边界：明确区分外部请求和内部调用
     */
    @Pointcut("execution(public * org.zerolg.aidemo2.controller..*.*(..))")
    public void webLog() {
        // 这是一个空方法，仅用于定义切点
        // 实际的切面逻辑在 @Around 注解的方法中实现
    }

    /**
     * 环绕通知：在目标方法执行前后进行日志记录
     *
     * @param joinPoint 连接点对象，包含目标方法的信息和参数
     * @return 目标方法的返回值
     * @throws Throwable 目标方法可能抛出的异常
     * @Around 注解说明：
     * 环绕通知是最强大的通知类型，可以完全控制目标方法的执行
     * - 可以在方法执行前后添加逻辑
     * - 可以修改方法参数和返回值
     * - 可以捕获和处理异常
     * - 必须调用 joinPoint.proceed() 来执行目标方法
     * <p>
     * 执行流程：
     * 1. 请求到达 Controller 方法
     * 2. 切面拦截，执行 doAround 方法
     * 3. 记录请求开始日志和 TraceId
     * 4. 调用 joinPoint.proceed() 执行实际的 Controller 方法
     * 5. Controller 方法执行完成，返回结果
     * 6. 记录请求结束日志和耗时
     * 7. 清理 MDC 上下文
     * 8. 返回结果给客户端
     */
    @Around("webLog()") // 引用上面定义的切点
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 记录请求开始时间，用于计算执行耗时
        long startTime = System.currentTimeMillis();

        // 1. 设置 TraceId - 为当前请求生成唯一标识
        if (MDC.get(TRACE_ID) == null) {
            // 生成 UUID 并移除连字符，作为 TraceId
            // UUID 保证全局唯一性，移除连字符使其更简洁
            MDC.put(TRACE_ID, UUID.randomUUID().toString().replace("-", ""));
        }

        // 2. 获取请求信息 - 从 Spring 上下文中获取 HTTP 请求对象
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        // 提取关键的请求信息
        String url = request.getRequestURL().toString();        // 完整的请求 URL
        String method = request.getMethod();                    // HTTP 方法 (GET/POST/PUT/DELETE)
        String ip = request.getRemoteAddr();                   // 客户端 IP 地址
        // 获取目标方法的完整名称（类名.方法名）
        String classMethod = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();

        // 3. 记录请求开始日志 - 使用统一的格式输出请求信息
        logger.info("========================================== Start ==========================================");
        logger.info("URL          : {}", url);           // 请求地址
        logger.info("HTTP Method  : {}", method);        // HTTP 方法
        logger.info("Class Method : {}", classMethod);   // 目标方法
        logger.info("IP           : {}", ip);            // 客户端 IP

        // 4. 记录请求参数 - 尝试打印方法参数（排除大对象和敏感对象）
        try {
            Object[] args = joinPoint.getArgs(); // 获取方法参数数组
            for (Object arg : args) {
                // 过滤掉不适合打印的参数类型
                if (!(arg instanceof HttpServletRequest) &&    // HTTP 请求对象（太大）
                        !(arg instanceof HttpServletResponse) &&   // HTTP 响应对象（太大）
                        !(arg instanceof MultipartFile)) {         // 文件上传对象（太大）
                    logger.info("Request Args : {}", arg);      // 打印其他参数
                }
            }
        } catch (Exception e) {
            // 忽略参数日志记录的异常，不影响主流程
            // 可能的异常：参数序列化失败、循环引用等
        }

        Object result = null;
        try {
            // 5. 执行目标方法 - 这是关键步骤，实际调用 Controller 方法
            result = joinPoint.proceed();
        } finally {
            // 6. 记录响应信息 - 无论方法成功还是异常，都要记录结束日志
            long costTime = System.currentTimeMillis() - startTime; // 计算执行耗时
            logger.info("Time Cost    : {} ms", costTime);          // 记录耗时
            logger.info("=========================================== End ===========================================");

            // 7. 清理 MDC 上下文 - 避免内存泄漏和线程污染
            // 在线程池环境中，线程会被复用，必须清理 MDC 避免影响后续请求
            MDC.remove(TRACE_ID);
        }

        // 8. 返回方法执行结果
        return result;
    }
}
