package org.zerolg.aidemo2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.zerolg.aidemo2.service.stream.IngestionConsumer;
import org.zerolg.aidemo2.service.stream.SessionEventConsumer;

import java.time.Duration;

import org.zerolg.aidemo2.constant.RedisKeys;

/**
 * Redis Stream 配置类
 *
 * 重点:
 * 1. 将 StreamMessageListenerContainer 定义为独立的 Bean。
 * 2. 设置 autoStartup = false，禁止容器在 Spring 初始化时自动启动。
 * 3. 容器的启动将由各自的消费者在成功初始化 Stream 和 Group 后手动触发。
 */
@Configuration
public class RedisStreamConfig {

    // --- Session Event Stream 配置 ---

    @Bean(name = "sessionEventContainer")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> sessionEventContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .serializer(new StringRedisSerializer())
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        return container;
    }

    @Bean
    public Subscription sessionEventSubscription(StreamMessageListenerContainer<String, MapRecord<String, String, String>> sessionEventContainer,
                                                 SessionEventConsumer consumer) {
        return sessionEventContainer.receive(
                Consumer.from("session-archiver-group", "consumer-1"),
                StreamOffset.create("session:event:stream", ReadOffset.lastConsumed()),
                consumer);
    }

    // --- Ingestion Stream 配置 ---

    @Bean(name = "ingestionContainer")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> ingestionContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        return container;
    }

    @Bean
    public Subscription ingestionSubscription(
            @org.springframework.beans.factory.annotation.Qualifier("ingestionContainer") StreamMessageListenerContainer<String, MapRecord<String, String, String>> ingestionContainer,
            IngestionConsumer consumer,
            StringRedisTemplate redisTemplate) {

        // 确保消费者组存在 (如果不存在则创建)
        try {
            redisTemplate.opsForStream().createGroup(RedisKeys.STREAM_DOCUMENT_INGESTION, "ingestion-worker-group");
        } catch (Exception e) {
            // 忽略 "BUSYGROUP Consumer Group name already exists" 异常
        }

        return ingestionContainer.receive(
                Consumer.from("ingestion-worker-group", "worker-1"), // 组名必须与 createGroup 一致
                StreamOffset.create(RedisKeys.STREAM_DOCUMENT_INGESTION, ReadOffset.lastConsumed()),
                consumer);
    }
}
