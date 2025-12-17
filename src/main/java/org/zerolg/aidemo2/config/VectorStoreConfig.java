package org.zerolg.aidemo2.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore; // ✅ 正确路径
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    /**
     * 读取配置文件中的参数，保持 application.yml 为单一事实来源
     */
    @Value("${spring.ai.vectorstore.pgvector.dimension:1536}")
    private int dimension;

    @Value("${spring.ai.vectorstore.pgvector.index-type:HNSW}")
    private PgVectorStore.PgIndexType indexType;

/*    @Value("${spring.ai.vectorstore.pgvector.distance-type:COSINE}")
    private PgVectorStore.PgDistanceType distanceType;*/

    @Value("${spring.ai.vectorstore.pgvector.initialize-schema:true}")
    private boolean initializeSchema;

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    /**
     * ✅ 显式定义 PgVectorStore Bean
     * 解决 "VectorStore required a bean ... that could not be found" 问题
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        // 构建配置对象
        // 注意：不同版本的 Spring AI 构造函数可能略有不同，
        // 如果 1.0.0 版本 API 有变，通常IDE会提示使用 PgVectorStoreOptions 或类似的 Builder
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dimension)
                .indexType(indexType)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(initializeSchema)
                .build();
    }
}