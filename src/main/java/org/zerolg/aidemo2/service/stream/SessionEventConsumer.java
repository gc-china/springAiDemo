package org.zerolg.aidemo2.service.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.model.SessionEvent;
import org.zerolg.aidemo2.service.SessionArchiver;

import java.util.Map;

/**
 * 会话事件消费者 (Redis Stream Listener)
 * 
 * 职责：
 * 监听 Redis Stream 中的会话事件，并调用归档服务进行处理。
 * 实现了 StreamListener 接口，由 RedisMessageListenerContainer 驱动。
 * 
 * 错误处理：
 * 包含了基本的异常捕获逻辑，未来将集成 Dead Letter Queue (DLQ)
 * 以处理无法消费的消息，防止消息丢失。
 */
@Service
public class SessionEventConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(SessionEventConsumer.class);
    private final SessionArchiver sessionArchiver;
    private final ObjectMapper objectMapper;

    public SessionEventConsumer(SessionArchiver sessionArchiver, ObjectMapper objectMapper) {
        this.sessionArchiver = sessionArchiver;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理接收到的 Stream 消息
     */
    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            // 1. 获取消息内容
            Map<String, String> value = message.getValue();
            String eventJson = value.get("payload");

            if (eventJson == null) {
                logger.warn("收到空负载消息，跳过: {}", message.getId());
                return;
            }

            // 2. 反序列化为 SessionEvent 对象
            SessionEvent event = objectMapper.readValue(eventJson, SessionEvent.class);

            // 3. 调用归档服务进行处理
            sessionArchiver.archive(event);

            // 注意：在自动确认模式下，容器会自动执行 XACK。
            // 如果配置为手动确认，这里需要手动调用 opsForStream().acknowledge()

        } catch (Exception e) {
            logger.error("处理会话事件失败: messageId={}", message.getId(), e);
            // TODO: 实现 Dead Letter Queue (DLQ) 逻辑
            // 将失败消息发送到 session:event:dlq
        }
    }
}
