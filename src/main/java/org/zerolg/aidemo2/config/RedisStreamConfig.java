package org.zerolg.aidemo2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.zerolg.aidemo2.service.stream.SessionEventConsumer;

import java.time.Duration;

/**
 * Redis Stream 配置类
 * 
 * 作用：
 * 配置 Redis Stream 的监听容器，用于异步消费会话事件（如消息创建、会话归档）。
 * 这种架构实现了“写操作”与“归档操作”的解耦，提高了系统的响应速度和可靠性。
 */
@Configuration
public class RedisStreamConfig {

    /**
     * 配置 Stream 消息监听容器
     * 
     * @param connectionFactory Redis 连接工厂
     * @param consumer          消息消费者 Bean
     * @return 订阅对象
     */
    @Bean
    public Subscription sessionEventSubscription(RedisConnectionFactory connectionFactory,
            SessionEventConsumer consumer) {

        // 1. 配置监听容器选项
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                // 设置轮询超时时间，避免空轮询占用过多 CPU
                .pollTimeout(Duration.ofMillis(100))
                .build();

        // 2. 创建监听容器
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container = StreamMessageListenerContainer
                .create(connectionFactory, options);

        // 3. 创建订阅
        // receive() 方法启动一个消费者来监听指定的 Stream
        Subscription subscription = container.receive(
                // 定义消费者组和消费者名称
                // Group: session-archiver-group (归档服务组)
                // Consumer: consumer-1 (当前实例消费者名，分布式环境下应唯一或自动生成)
                Consumer.from("session-archiver-group", "consumer-1"),

                // 指定要监听的 Stream Key 和读取偏移量
                // ReadOffset.lastConsumed() 表示从上次消费的位置继续读取，保证不丢失消息
                StreamOffset.create("session:event:stream", ReadOffset.lastConsumed()),

                // 指定处理消息的消费者逻辑
                consumer);

        // 4. 启动容器
        container.start();
        return subscription;
    }
}
