package com.example.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inventory")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Integer totalStock;
    private Integer lockedStock;
    private Integer availableStock;
    @Version
    private Integer version;
    private LocalDateTime updatedAt;
}
