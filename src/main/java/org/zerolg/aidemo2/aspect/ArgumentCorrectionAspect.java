package org.zerolg.aidemo2.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.common.ToolExecutionResult;
import org.zerolg.aidemo2.service.MockSearchService;
import org.zerolg.aidemo2.service.MockSearchService.SearchResult;
import org.zerolg.aidemo2.tools.InventoryTools.StockQueryRequest;
import org.zerolg.aidemo2.tools.InventoryTools.TransferRequest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 参数矫正切面 - 统一拦截查询和调拨服务
 */
@Aspect
@Component
public class ArgumentCorrectionAspect {

    private static final Logger logger = LoggerFactory.getLogger(ArgumentCorrectionAspect.class);
    private final MockSearchService searchService;

    public ArgumentCorrectionAspect(MockSearchService searchService) {
        this.searchService = searchService;
    }

    // ✅ 修正切点：同时拦截 StockQueryService 和 TransferToolService
    @Around("execution(* org.zerolg.aidemo2.service.stock.StockQueryService.queryStock(..)) || " +
            "execution(* org.zerolg.aidemo2.service.stock.TransferToolService.executeTransfer(..))")
    public Object correctArguments(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        if (args.length == 0) {
            return joinPoint.proceed();
        }

        Object request = args[0];
        String rawProduct = null;
        boolean isTransfer = false;

        // 1. 提取产品名称 (支持两种 Request 类型)
        if (request instanceof StockQueryRequest q) {
            rawProduct = q.product();
        } else if (request instanceof TransferRequest t) {
            rawProduct = t.product();
            isTransfer = true;
        } else {
            // 其他未知参数类型，直接放行
            return joinPoint.proceed();
        }

        // 2. 检查是否已经是标准 ID (P-开头)
        if (rawProduct != null && rawProduct.startsWith("P-")) {
            logger.debug("✅ 参数已经是ID格式，放行: {}", rawProduct);
            return joinPoint.proceed();
        }

        logger.info("🛑 [AOP] 拦截到模糊参数: [{}], 启动搜索引擎矫正...", rawProduct);

        // 3. 调用搜索引擎
        List<SearchResult> matches = searchService.fuzzySearch(rawProduct);

        // 4. 决策逻辑
        if (matches.size() == 1) {
            // ✅ 情况A: 唯一匹配 -> 自动矫正
            SearchResult match = matches.get(0);
            logger.info("✅ [AOP] 自动矫正成功: {} -> {} ({})", rawProduct, match.name(), match.id());

            // 构造新参数 (区分类型)
            Object newRequest;
            if (isTransfer) {
                TransferRequest old = (TransferRequest) request;
                // Record 是不可变的，必须用构造函数创建新的
                newRequest = new TransferRequest(
                        match.id(), // 替换为 ID
                        old.fromWarehouse(),
                        old.toWarehouse(),
                        old.quantity(),
                        old.confirmed()
                );
            } else {
                newRequest = new StockQueryRequest(match.id());
            }

            return joinPoint.proceed(new Object[]{newRequest});

        } else if (matches.size() > 1) {
            // ❓ 情况B: 歧义 -> 返回结构化提示
            String names = matches.stream()
                    .map(SearchResult::name)
                    .collect(Collectors.joining(", "));
            logger.warn("❓ [AOP] 发现歧义: {} -> [{}]", rawProduct, names);

            return ToolExecutionResult.ambiguous(
                    matches,
                    "找到多个相关产品: " + names + "。请向用户澄清具体是指哪一个。"
            );

        } else {
            // ❌ 情况C: 无匹配 -> 返回错误
            logger.warn("❌ [AOP] 未找到匹配: {}", rawProduct);
            return ToolExecutionResult.notFound(
                    "未找到名称包含 '" + rawProduct + "' 的产品。请检查名称是否正确。"
            );
        }
    }
}