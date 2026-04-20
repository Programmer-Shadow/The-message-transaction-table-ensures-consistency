package com.example.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.domain.OutboxMessage;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxMessageRepository extends BaseMapper<OutboxMessage> {

    @Select("SELECT * FROM outbox_message " +
            "WHERE status = 'PENDING' AND next_retry_at <= NOW() AND retry_count < max_retry " +
            "ORDER BY created_at ASC LIMIT #{limit}")
    List<OutboxMessage> findPendingMessages(@Param("limit") int limit);

    // CAS 更新，返回受影响行数，0 表示已被其他实例抢占
    @Update("UPDATE outbox_message SET status = 'PROCESSING' WHERE id = #{id} AND status = 'PENDING'")
    int casUpdateToProcessing(@Param("id") Long id);

    @Update("UPDATE outbox_message SET status = 'SENT', sent_at = #{sentAt} WHERE id = #{id}")
    void markAsSent(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt);

    @Update("UPDATE outbox_message SET status = 'PENDING', retry_count = #{retryCount}, " +
            "next_retry_at = #{nextRetryAt}, error_message = #{errorMessage} WHERE id = #{id}")
    void markAsRetry(@Param("id") Long id,
                     @Param("retryCount") int retryCount,
                     @Param("nextRetryAt") LocalDateTime nextRetryAt,
                     @Param("errorMessage") String errorMessage);

    @Update("UPDATE outbox_message SET status = 'FAILED', error_message = #{errorMessage} WHERE id = #{id}")
    void markAsFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);
}
