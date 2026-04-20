package com.example.warehouse.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shipment")
public class Shipment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long productId;
    private Integer quantity;
    // PENDING / SHIPPED / DELIVERED
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
