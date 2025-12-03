package org.zerolg.aidemo2.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * VectorStore 配置类
 * 
 * 注意：Spring AI 1.0.0 使用自动配置方式配置 PgVector
 * 通过 application.yml 配置 spring.ai.vectorstore.pgvector 即可
 * 无需手动创建 PgVectorStore Bean
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    /**
     * 手动配置 VectorStore（仅在自动配置失败时使用）
     * 当前使用 application.yml 自动配置，此 Bean 仅作为备份
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.vectorstore.pgvector.enabled", havingValue = "false", matchIfMissing = false)
    public VectorStore fallbackVectorStore(EmbeddingModel embeddingModel) {
        // 如果 PgVector 自动配置失败，使用内存存储作为降级方案
        return org.springframework.ai.vectorstore.SimpleVectorStore.builder(embeddingModel).build();
    }
}
