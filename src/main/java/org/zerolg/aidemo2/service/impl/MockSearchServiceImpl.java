package org.zerolg.aidemo2.service.impl;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.zerolg.aidemo2.service.MockSearchService;

/**
 * 模拟搜索引擎服务实现
 * 生产环境通常使用 ElasticSearch 或 Redis
 */
@Service
@Primary
public class MockSearchServiceImpl implements MockSearchService {

    // 模拟数据库：名称 -> ID 的映射
    // 包含一些同义词和模糊匹配的数据
    private static final Map<String, String> PRODUCT_DB = new HashMap<>();

    static {
        PRODUCT_DB.put("iPhone 15", "P-001");
        PRODUCT_DB.put("iPhone 15 Pro", "P-002");
        PRODUCT_DB.put("MacBook Air M2", "P-003");
        PRODUCT_DB.put("Sony WH-1000XM5", "P-004");
        PRODUCT_DB.put("Dyson Hair Dryer", "P-005");
    }

    /**
     * 模糊搜索
     * @param query 用户输入的模糊名称
     * @return 匹配的产品列表
     */
    @Override
    public List<SearchResult> fuzzySearch(String query) {
        List<SearchResult> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        for (Map.Entry<String, String> entry : PRODUCT_DB.entrySet()) {
            String name = entry.getKey();
            String lowerName = name.toLowerCase();
            if (lowerName.equals(lowerQuery)) {
                results.add(new SearchResult(name, entry.getValue()));

            }
            return results;
        }


        for (Map.Entry<String, String> entry : PRODUCT_DB.entrySet()) {
            String name = entry.getKey();
            String lowerName = name.toLowerCase();

            // 简单的模糊匹配逻辑：包含关系
            // 生产环境会使用更复杂的算法（如编辑距离、拼
            // 音匹配、向量相似度）
            if (lowerName.contains(lowerQuery) || isSynonym(lowerQuery, lowerName)) {
                results.add(new SearchResult(entry.getValue(), name));
            }
        }
        return results;
    }

    // 简单的同义词处理
    private boolean isSynonym(String query, String target) {
        if (query.contains("苹果") && target.contains("iphone")) return true;
        if (query.contains("电脑") && target.contains("macbook")) return true;
        if (query.contains("吹风机") && target.contains("dryer")) return true;
        return false;
    }
}
