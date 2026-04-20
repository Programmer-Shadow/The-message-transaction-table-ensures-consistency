package com.example.order.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class CreateOrderRequest {

    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotNull(message = "productId 不能为空")
    private Long productId;

    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;

    // 客户端生成，用于幂等（相同 orderNo 重试不会重复下单）
    private String orderNo;
}
