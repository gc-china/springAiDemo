package org.zerolg.aidemo2.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter; // ✅ M3 正确包路径
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        // ✅ M3 中可以直接 new，使用默认配置，无需注入 Tokenizer
        return new TokenTextSplitter();
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {

        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel)
                .build();
        return vectorStore;
    }
}
