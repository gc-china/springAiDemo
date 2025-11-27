package org.zerolg.aidemo2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class VectorStoreConfig {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel)
                .build();
        
        File vectorStoreFile = new File("vectorstore.json");
        if (vectorStoreFile.exists()) {
            vectorStore.load(vectorStoreFile);
            logger.info("Loaded vector store from file: {}", vectorStoreFile.getAbsolutePath());
        } else {
            logger.info("No existing vector store file found, starting fresh.");
        }
        
        return vectorStore;
    }
}
