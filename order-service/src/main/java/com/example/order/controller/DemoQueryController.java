package com.example.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.order.domain.Inventory;
import com.example.order.domain.Order;
import com.example.order.domain.OutboxMessage;
import com.example.order.repository.InventoryRepository;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仅用于 Demo 学习观察，生产环境不需要此 Controller。
 * 前端页面通过这些接口实时查询数据库状态，可视化 Outbox Pattern 的状态流转。
 */
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoQueryController {

    private final OrderRepository orderRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final InventoryRepository inventoryRepository;

    @GetMapping("/orders")
    public List<Order> listOrders() {
        return orderRepository.selectList(
                new LambdaQueryWrapper<Order>().orderByDesc(Order::getCreatedAt).last("LIMIT 20")
        );
    }

    /**
     * 查询 Outbox 消息状态（Demo 核心观察点）
     * 下单后刷新此接口，可看到消息从 PENDING → PROCESSING → SENT 的变化
     */
    @GetMapping("/outbox")
    public List<OutboxMessage> listOutbox() {
        return outboxMessageRepository.selectList(
                new LambdaQueryWrapper<OutboxMessage>().orderByDesc(OutboxMessage::getCreatedAt).last("LIMIT 30")
        );
    }

    @GetMapping("/inventory")
    public List<Inventory> listInventory() {
        return inventoryRepository.selectList(null);
    }
}
