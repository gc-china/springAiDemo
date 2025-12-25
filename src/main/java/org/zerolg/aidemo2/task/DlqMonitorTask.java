package org.zerolg.aidemo2.task;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 死信队列(DLQ) 监控任务
 * 定期检查 Redis 中的死信队列，如果有积压消息，触发告警日志。
 */
@Component
@RequiredArgsConstructor
public class DlqMonitorTask {

    private static final Logger logger = LoggerFactory.getLogger(DlqMonitorTask.class);
    // 假设 DLQ 的 Key 命名规范
    private static final String DLQ_KEY = "aidemo:session:dlq";
    private final StringRedisTemplate redisTemplate;

    /**
     * 每 5 分钟检查一次 DLQ
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void checkDeadLetterQueue() {
        try {
            Long size = redisTemplate.opsForList().size(DLQ_KEY);

            if (size != null && size > 0) {
                // 触发告警：实际生产中这里可以发送钉钉/Slack通知，或者抛出特定异常供 Prometheus 抓取
                logger.error("🚨 [CRITICAL] 死信队列告警! 当前堆积数量: {}. 请检查 Key: {}", size, DLQ_KEY);

                // 可选：采样打印一条死信内容以便排查
                String lastError = redisTemplate.opsForList().index(DLQ_KEY, 0);
                logger.error("   -> 最新死信样本: {}", lastError);
            } else {
                logger.debug("DLQ 状态正常，无积压。");
            }
        } catch (Exception e) {
            logger.error("监控 DLQ 时发生异常", e);
        }
    }
}