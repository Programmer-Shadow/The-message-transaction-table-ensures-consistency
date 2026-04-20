package com.example.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.warehouse.domain.IdempotentRecord;
import com.example.warehouse.repository.IdempotentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * ================================================================
 * 【消费端幂等设计思路】
 *
 * 为什么需要幂等？
 *   MQ 的消息投递保证是"At Least Once"（至少一次），
 *   即同一条消息可能被投递多次（网络超时、消费者重启等）。
 *   如果不做幂等处理，同一订单可能触发多次发货，造成超发。
 *
 * 本方案的幂等原理：
 *   利用 idempotent_record 表的 UNIQUE KEY(message_id)，
 *   在同一个 @Transactional 事务内完成：
 *     1. INSERT 幂等记录（status=PROCESSING）
 *     2. 执行业务逻辑（createShipment）
 *     3. UPDATE 幂等记录（status=SUCCESS）
 *
 *   如果第二次消费同一条消息：
 *     INSERT 触发 DuplicateKeyException
 *     → 查到 status=SUCCESS → 直接返回，不重复执行业务
 *
 * 为什么把幂等检查和业务执行放在同一个事务？
 *   如果分开：
 *     检查 → 没有记录 → （此时另一个线程也在消费） → 都去执行 → 重复！
 *   放在同一事务内，INSERT 行锁保证只有一个线程能成功插入，另一个会等待或冲突。
 * ================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentService {

    private final IdempotentRecordRepository idempotentRecordRepository;

    private static final String CONSUMER_GROUP = "warehouse-consumer-group";

    @Transactional(rollbackFor = Exception.class)
    public void executeIdempotent(String messageId, Runnable businessLogic) {
        IdempotentRecord record = new IdempotentRecord();
        record.setMessageId(messageId);
        record.setConsumerGroup(CONSUMER_GROUP);
        record.setStatus("PROCESSING");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        try {
            // 尝试插入幂等记录，UNIQUE KEY 保证并发安全
            idempotentRecordRepository.insert(record);
        } catch (DuplicateKeyException e) {
            // messageId 已存在，说明之前处理过或正在处理
            IdempotentRecord existing = idempotentRecordRepository.selectOne(
                    new LambdaQueryWrapper<IdempotentRecord>()
                            .eq(IdempotentRecord::getMessageId, messageId));

            if (existing == null) {
                throw new RuntimeException("幂等记录状态异常: messageId=" + messageId);
            }
            if ("SUCCESS".equals(existing.getStatus())) {
                // 已成功处理过，直接跳过（幂等保护生效）
                log.info("幂等跳过，消息已成功处理: messageId={}", messageId);
                return;
            }
            // PROCESSING 状态：上次处理过程中崩溃（进程被 kill 等），允许重新处理
            log.warn("检测到未完成的PROCESSING记录（可能是上次崩溃），重新处理: messageId={}", messageId);
        }

        try {
            // 执行真正的业务逻辑（createShipment），Propagation.MANDATORY 保证在当前事务内
            businessLogic.run();
            // 业务成功，更新幂等状态为 SUCCESS
            idempotentRecordRepository.updateStatus(messageId, "SUCCESS");
            log.info("消息处理成功: messageId={}", messageId);
        } catch (Exception e) {
            // 业务失败，标记 FAILED，事务回滚，MQ 会重新投递
            idempotentRecordRepository.updateStatus(messageId, "FAILED");
            log.error("消息处理失败: messageId={}, error={}", messageId, e.getMessage());
            throw e;
        }
    }
}
