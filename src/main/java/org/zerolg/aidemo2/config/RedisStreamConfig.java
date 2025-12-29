package org.zerolg.aidemo2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

@Configuration
public class RedisStreamConfig {

    /**
     * Session Event Stream 监听容器 Bean
     * 由 SessionEventConsumer 使用
     */
    @Bean("sessionEventContainer")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> sessionEventContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .serializer(new StringRedisSerializer())
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    /**
     * Ingestion Stream 监听容器 Bean
     * 由 IngestionConsumer 使用
     */
    @Bean("ingestionContainer")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> ingestionContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .serializer(new StringRedisSerializer())
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }
}
