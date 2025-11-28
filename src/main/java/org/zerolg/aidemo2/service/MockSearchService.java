package org.zerolg.aidemo2.service;

import java.util.List;

/**
 * 产品搜索服务接口
 * 定义模糊搜索规范，支持后续切换到 ElasticSearch/Solr
 */
public interface MockSearchService {

    /**
     * 搜索结果记录
     */
    record SearchResult(String id, String name) {}

    /**
     * 模糊搜索产品
     * @param query 查询关键词
     * @return 匹配结果列表
     */
    List<SearchResult> fuzzySearch(String query);
}
