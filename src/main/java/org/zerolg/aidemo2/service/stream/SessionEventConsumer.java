package org.zerolg.aidemo2.service.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.zerolg.aidemo2.model.SessionEvent;

import java.util.Map;
import java.util.Objects;

@Service
public class SessionEventConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(SessionEventConsumer.class);
    private final ObjectMapper objectMapper;
    private static final String STREAM_KEY = "session:event:stream";
    private static final String GROUP_NAME = "session-archiver-group";
    private final StringRedisTemplate redisTemplate;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    @Autowired
    public SessionEventConsumer(ObjectMapper objectMapper,
                                StringRedisTemplate redisTemplate,
                                @Qualifier("sessionEventContainer") StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.container = container;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeAndStartContainer() {
        try {
            // 步骤 1: 检查并清理
            Boolean hasKey = redisTemplate.hasKey(STREAM_KEY);
            if (Boolean.TRUE.equals(hasKey) && !Objects.equals(redisTemplate.type(STREAM_KEY), DataType.STREAM)) {
                logger.warn("Key '{}' 存在但类型错误，将删除。", STREAM_KEY);
                redisTemplate.delete(STREAM_KEY);
                hasKey = false;
            }

            // 步骤 2: 创建 Stream
            if (Boolean.FALSE.equals(hasKey)) {
                redisTemplate.opsForStream().add(STREAM_KEY, Map.of("init", "stream_created"));
                logger.info("Stream '{}' 已创建。", STREAM_KEY);
            }

            // 步骤 3: 创建消费者组
            try {
                redisTemplate.opsForStream().createGroup(STREAM_KEY, GROUP_NAME);
                logger.info("消费者组 '{}' 已创建。", GROUP_NAME);
            } catch (Exception e) {
                if (isBusyGroupException(e)) {
                    logger.info("消费者组 '{}' 已存在。", GROUP_NAME);
                } else {
                    throw e;
                }
            }

            // 步骤 4: 启动容器
            if (!container.isRunning()) {
                container.start();
                logger.info("SessionEventContainer 已启动。");
            }

        } catch (Exception e) {
            logger.error("初始化并启动 SessionEventContainer 失败。", e);
        }
    }

    private boolean isBusyGroupException(Throwable e) {
        if (e == null) return false;
        if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) return true;
        return isBusyGroupException(e.getCause());
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> value = message.getValue();
            if (value.containsKey("init")) {
                redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, message.getId());
                return;
            }

            String eventJson = value.get("payload");
            if (eventJson == null) {
                logger.warn("收到空负载消息，跳过: {}", message.getId());
                redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, message.getId());
                return;
            }

            SessionEvent event = objectMapper.readValue(eventJson, SessionEvent.class);

            // 冲突修复：删除实时归档逻辑！
            // sessionArchiver.archive(event); 
            // 现在数据只留在 Redis 中，等待 SessionArchiverTask 定时任务在 7 天后将其搬运到 DB。

            redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, message.getId());

        } catch (Exception e) {
            logger.error("处理会话事件失败: messageId={}", message.getId(), e);
        }
    }
}
