package org.zerolg.aidemo2.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Redis Stream 自定义监控指标
 * 暴露 Stream 的 Lag (积压量) 到 Actuator
 */
@Component
@RequiredArgsConstructor
public class RedisStreamMetrics implements MeterBinder {

    private static final Logger logger = LoggerFactory.getLogger(RedisStreamMetrics.class);
    // 监听的目标 Stream 和 Group
    private static final String STREAM_KEY = "aidemo:session:event:stream";
    private static final String GROUP_NAME = "aidemo-group";
    private final StringRedisTemplate redisTemplate;

    @Override
    public void bindTo(MeterRegistry registry) {
        // 定义一个 Gauge (仪表盘)，它会动态调用 lambda 获取数值
        Gauge.builder("aidemo.redis.stream.lag", this, RedisStreamMetrics::getStreamLag)
                .description("Redis Stream 消费者组积压消息数")
                .tag("stream", STREAM_KEY)
                .tag("group", GROUP_NAME)
                .register(registry);
    }

    /**
     * 获取积压量 (Lag)
     * 注意：XINFO 命令有一定开销，Micrometer 默认约 1分钟采样一次，是可以接受的。
     */
    private long getStreamLag() {
        try {
            // 修复: 旧版 Spring Data Redis 缺少 lag() 方法，改用原生命令执行
            // 原生命令: XINFO GROUPS <key>
            Object rawResult = redisTemplate.execute((RedisCallback<Object>) connection ->
                    connection.execute("XINFO", "GROUPS".getBytes(StandardCharsets.UTF_8), STREAM_KEY.getBytes(StandardCharsets.UTF_8))
            );

            if (rawResult instanceof List) {
                List<?> groups = (List<?>) rawResult;
                for (Object groupObj : groups) {
                    if (groupObj instanceof List) {
                        List<?> groupDetails = (List<?>) groupObj;
                        String currentGroupName = null;
                        Long lag = 0L;

                        // 解析 Redis 返回的扁平 key-value 列表: ["name", "g1", "consumers", 1, "lag", 5, ...]
                        for (int i = 0; i < groupDetails.size() - 1; i += 2) {
                            String key = safeToString(groupDetails.get(i));
                            Object val = groupDetails.get(i + 1);

                            if ("name".equals(key)) {
                                currentGroupName = safeToString(val);
                            } else if ("lag".equals(key)) {
                                lag = safeToLong(val);
                            }
                        }

                        if (GROUP_NAME.equals(currentGroupName)) {
                            return lag;
                        }
                    }
                }
            }
            return 0L;
        } catch (Exception e) {
            // Stream 可能还未创建，或者 Redis 连接异常
            logger.debug("无法获取 Stream Lag 指标: {}", e.getMessage());
            return 0L;
        }
    }

    private String safeToString(Object obj) {
        if (obj instanceof byte[]) return new String((byte[]) obj, StandardCharsets.UTF_8);
        return obj != null ? obj.toString() : null;
    }

    private Long safeToLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof byte[]) {
            try {
                return Long.parseLong(new String((byte[]) obj, StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }
}