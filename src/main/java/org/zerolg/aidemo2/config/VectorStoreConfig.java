package org.zerolg.aidemo2.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    // VectorStore is now auto-configured by
    // spring-ai-pgvector-store-spring-boot-starter
    // based on application.yml configuration.
}
