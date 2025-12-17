package org.zerolg.aidemo2.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zerolg.aidemo2.tools.InventoryTools.StockQueryRequest;
import org.zerolg.aidemo2.service.MockSearchService;
import org.zerolg.aidemo2.service.MockSearchService.SearchResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 参数矫正切面 - 方案四的核心实现
 * 拦截工具调用，利用搜索引擎矫正参数
 */
@Aspect
@Component
public class ArgumentCorrectionAspect {

    private static final Logger logger = LoggerFactory.getLogger(ArgumentCorrectionAspect.class);
    private final MockSearchService searchService;

    public ArgumentCorrectionAspect(MockSearchService searchService) {
        this.searchService = searchService;
    }

    // 拦截 InventoryTools 中的 queryStock 方法
    @Around("execution(* org.zerolg.aidemo2.tools.InventoryTools.queryStock(..))")
    public Object correctArguments(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        // 确保参数类型正确
        if (args.length > 0 && args[0] instanceof StockQueryRequest) {
            StockQueryRequest originalRequest = (StockQueryRequest) args[0];
            String rawName = originalRequest.product();

            // 1. 如果已经是 ID 格式 (P-开头)，直接放行
            if (rawName.startsWith("P-")) {
                logger.debug("参数已经是ID格式，放行: {}", rawName);
                return joinPoint.proceed();
            }

            logger.info("🛑 拦截到模糊参数: [{}],正在进行搜索引擎矫正...", rawName);

            // 2. 调用搜索引擎
            List<SearchResult> matches = searchService.fuzzySearch(rawName);

            // 3. 决策逻辑
            if (matches.size() == 1) {
                // ✅ 情况A: 唯一匹配 -> 自动矫正
                SearchResult match = matches.get(0);
                logger.info("✅ 找到唯一匹配: {} -> {} ({})", rawName, match.name(), match.id());

                // 偷梁换柱：创建新的 Request 对象，替换原来的参数
                StockQueryRequest newRequest = new StockQueryRequest(match.id());
                Object[] newArgs = new Object[]{newRequest};

                return joinPoint.proceed();

            } else if (matches.size() > 1) {
                // ❓ 情况B: 多个匹配 -> 返回歧义提示
                String names = matches.stream()
                        .map(SearchResult::name)
                        .collect(Collectors.joining(", "));
                logger.warn("❓ 发现歧义: {} -> [{}]", rawName, names);

                return "找到多个相关产品: " + names + "。请问您具体是指哪一个？";

            } else {
                // ❌ 情况C: 无匹配 -> 返回错误
                logger.warn("❌ 未找到匹配: {}", rawName);
                return "未找到名称包含 '" + rawName + "' 的产品。请检查名称是否正确。";
            }
        }

        return joinPoint.proceed();
    }
}
