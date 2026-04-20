package com.example.warehouse.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("idempotent_record")
public class IdempotentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String consumerGroup;
    // PROCESSING / SUCCESS / FAILED
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
