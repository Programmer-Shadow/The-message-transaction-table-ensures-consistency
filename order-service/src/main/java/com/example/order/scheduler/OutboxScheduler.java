package com.example.order.scheduler;

import com.example.order.domain.OutboxMessage;
import com.example.order.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ================================================================
 * 【Outbox Pattern 中的 Scheduler 角色】
 *
 * Scheduler 的职责：
 *   作为"消息投递保障员"，定期扫描 outbox 表中未发送的记录，
 *   把消息从"本地数据库"搬运到"RocketMQ"，并更新状态。
 *
 * 为什么需要 Scheduler，而不是直接在业务方法里发 MQ？
 *   如果在 @Transactional 方法里发 MQ，事务还没提交，消费者可能先收到消息
 *   就去查订单，但订单还没写进去（或者DB提交失败了消息已发出）。
 *   Scheduler 在事务提交之后才异步发送，彻底避免这个竞态问题。
 *
 * 多实例部署时如何防止重复发送？
 *   通过 CAS（Compare-And-Set）乐观锁机制：
 *   UPDATE outbox_message SET status='PROCESSING' WHERE id=? AND status='PENDING'
 *   只有 affected_rows=1 才说明当前实例抢到了这条消息，其他实例会跳过。
 *   这是一种"无外部依赖的分布式锁"，只利用数据库行级锁实现。
 * ================================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxMessageRepository outboxMessageRepository;
    private final RocketMQTemplate rocketMQTemplate;

    private static final int BATCH_SIZE = 100;

    @Scheduled(fixedDelayString = "${outbox.scheduler.interval:5000}")
    public void scanAndSend() {
        List<OutboxMessage> messages = outboxMessageRepository.findPendingMessages(BATCH_SIZE);
        for (OutboxMessage message : messages) {
            processMessage(message);
        }
    }

    private void processMessage(OutboxMessage message) {
        // CAS 抢占：利用数据库行锁实现轻量级分布式锁
        // 返回 0 说明被其他实例抢先改为 PROCESSING，当前实例跳过
        int affected = outboxMessageRepository.casUpdateToProcessing(message.getId());
        if (affected == 0) {
            return;
        }

        try {
            sendToRocketMQ(message);
            outboxMessageRepository.markAsSent(message.getId(), LocalDateTime.now());
            log.info("消息发送成功: messageId={}", message.getMessageId());
        } catch (Exception e) {
            log.error("消息发送失败，进入重试: messageId={}, retryCount={}, error={}",
                    message.getMessageId(), message.getRetryCount() + 1, e.getMessage());

            int newRetryCount = message.getRetryCount() + 1;
            if (newRetryCount >= message.getMaxRetry()) {
                // 达到最大重试次数，标记为 FAILED，需人工介入排查
                outboxMessageRepository.markAsFailed(message.getId(), e.getMessage());
                log.error("消息达到最大重试次数，标记FAILED，请人工处理: messageId={}", message.getMessageId());
            } else {
                // 指数退避：避免 MQ 故障期间频繁重试造成压力
                LocalDateTime nextRetry = calculateNextRetryAt(newRetryCount);
                outboxMessageRepository.markAsRetry(message.getId(), newRetryCount, nextRetry, e.getMessage());
            }
        }
    }

    private void sendToRocketMQ(OutboxMessage message) {
        String destination = message.getTag() != null
                ? message.getTopic() + ":" + message.getTag()
                : message.getTopic();

        // KEYS 设置为 messageId，消费端通过 message.getKeys() 获取用于幂等校验
        SendResult result = rocketMQTemplate.syncSend(destination,
                MessageBuilder.withPayload(message.getPayload())
                        .setHeader("KEYS", message.getMessageId())
                        .build());

        if (result.getSendStatus() != SendStatus.SEND_OK) {
            throw new RuntimeException("RocketMQ 发送状态异常: " + result.getSendStatus());
        }
    }

    /**
     * 指数退避计算：第1次失败等5s，第2次10s，第3次20s，最长3600s
     * 防止下游故障期间 Scheduler 频繁重试打爆系统
     */
    private LocalDateTime calculateNextRetryAt(int retryCount) {
        long backoffSeconds = Math.min(5L * (1L << retryCount), 3600L);
        return LocalDateTime.now().plusSeconds(backoffSeconds);
    }
}
