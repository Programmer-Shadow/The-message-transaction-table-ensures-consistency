package com.example.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("outbox_message")
public class OutboxMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String aggregateType;
    private String aggregateId;
    private String topic;
    private String tag;
    // JSON 快照，存完整事件数据
    private String payload;
    // PENDING / PROCESSING / SENT / FAILED
    private String status;
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime nextRetryAt;
    private LocalDateTime sentAt;
    private String errorMessage;
    private LocalDateTime createdAt;
}
