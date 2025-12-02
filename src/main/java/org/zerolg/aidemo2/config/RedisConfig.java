package org.zerolg.aidemo2.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 
 * 原理说明：
 * 1. 配置 RedisTemplate，用于操作 Redis
 * 2. 配置序列化器，将 Java 对象转换为 Redis 可存储的格式
 * 3. 使用 Jackson 进行 JSON 序列化，便于调试和跨语言兼容
 * 
 * 为什么需要自定义配置：
 * - Spring Boot 默认使用 JDK 序列化，不可读且效率低
 * - JSON 序列化可读性好，便于调试和监控
 * - 支持跨语言（其他语言也能读取 Redis 中的数据）
 * 
 * 序列化器选择：
 * - Key: StringRedisSerializer（字符串）
 * - Value: Jackson2JsonRedisSerializer（JSON）
 * - HashKey: StringRedisSerializer（字符串）
 * - HashValue: Jackson2JsonRedisSerializer（JSON）
 * 
 * @author zerolg
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate
     * 
     * RedisTemplate 是 Spring Data Redis 的核心类，提供了丰富的 Redis 操作方法：
     * - opsForValue(): 操作字符串（String）
     * - opsForList(): 操作列表（List）
     * - opsForSet(): 操作集合（Set）
     * - opsForZSet(): 操作有序集合（Sorted Set）
     * - opsForHash(): 操作哈希（Hash）
     * 
     * 为什么使用 RedisTemplate<String, Object>：
     * - Key 统一使用 String 类型，便于管理和查询
     * - Value 使用 Object 类型，支持存储不同类型的对象
     * 
     * @param connectionFactory Redis 连接工厂（Spring Boot 自动配置）
     * @return 配置好的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // 创建 RedisTemplate 实例
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        
        // 设置连接工厂
        // connectionFactory 由 Spring Boot 根据 application.yml 中的配置自动创建
        // 包含了连接池、超时时间等配置
        template.setConnectionFactory(connectionFactory);

        // ==================== 配置序列化器 ====================
        
        // 1. 创建 Jackson ObjectMapper
        // ObjectMapper 是 Jackson 的核心类，负责 Java 对象和 JSON 的转换
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 注册 JavaTimeModule，支持 Java 8 时间类型（如 Instant、LocalDateTime）
        // 如果不注册，序列化 Instant 等类型会报错
        objectMapper.registerModule(new JavaTimeModule());
        
        // 2. 创建 JSON 序列化器
        // Jackson2JsonRedisSerializer 将 Java 对象序列化为 JSON 字符串
        Jackson2JsonRedisSerializer<Object> jsonSerializer = 
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
        
        // 3. 创建字符串序列化器
        // StringRedisSerializer 将字符串按 UTF-8 编码存储
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // ==================== 设置序列化器 ====================
        
        // Key 使用字符串序列化器
        // 例如：session:messages:conv-123 → "session:messages:conv-123"
        template.setKeySerializer(stringSerializer);
        
        // Value 使用 JSON 序列化器
        // 例如：SessionMessage 对象 → {"id":"msg-1","role":"user",...}
        template.setValueSerializer(jsonSerializer);
        
        // Hash Key 使用字符串序列化器
        // 例如：userId → "userId"
        template.setHashKeySerializer(stringSerializer);
        
        // Hash Value 使用 JSON 序列化器
        // 例如：user-123 → "user-123"（如果是字符串）
        //       或 SessionMetadata 对象 → {"userId":"user-123",...}
        template.setHashValueSerializer(jsonSerializer);

        // 初始化 RedisTemplate
        // 这一步会验证配置并建立连接
        template.afterPropertiesSet();
        
        return template;
    }

    /**
     * 配置 ObjectMapper Bean
     * 
     * 为什么单独配置：
     * - 可以在其他地方注入使用（如手动序列化/反序列化）
     * - 统一配置，保证序列化行为一致
     * 
     * @return 配置好的 ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册 Java 8 时间模块
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
