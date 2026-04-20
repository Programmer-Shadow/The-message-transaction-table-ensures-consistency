package com.example.order.service;

import com.example.order.domain.Order;
import com.example.order.domain.OutboxMessage;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderCreatedEvent;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OutboxMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ================================================================
 * 【Outbox Pattern 核心思路】
 *
 * 问题根源：分布式系统中，"写DB"和"发MQ"是两个独立的操作，
 *          无法用同一个事务包裹，天然存在原子性问题。
 *
 * 方案一（先写DB再发MQ，有问题）：
 *   saveOrder();   // DB提交成功
 *   sendMQ();      // ← 如果这里崩溃，消息永久丢失！仓储永远不知道有订单
 *
 * 方案二（先发MQ再写DB，同样有问题）：
 *   sendMQ();      // 消息已发出
 *   saveOrder();   // ← 如果DB提交失败，消息已发但订单没存！仓储多发货了
 *
 * Outbox 方案（本方案）：
 *   把"发MQ"这件事，转化为"写一行DB记录"这件事。
 *   DB事务是可靠的 → 订单表 + outbox表 同一事务，要么全成功，要么全回滚。
 *   Scheduler 异步读 outbox 表，负责把消息真正投递到 MQ。
 *   最坏情况：Scheduler 崩溃，重启后继续扫描 PENDING 消息，最终投递成功。
 *
 *   核心洞察：用"可靠的DB写入"来保存"消息投递的意图"，
 *             把不可靠的 MQ 投递推迟到事务之外，并支持重试。
 * ================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    /**
     * 下单的完整事务：扣库存 + 建订单 + 写outbox，三步在同一个DB事务内完成。
     * 任何一步失败，三步全部回滚。客户端不会看到"订单建了但没消息"的中间状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(CreateOrderRequest request) {
        String orderNo = request.getOrderNo() != null
                ? request.getOrderNo()
                : "ORD-" + System.currentTimeMillis();

        // 步骤1：扣减库存（Propagation.MANDATORY，在当前事务内执行）
        inventoryService.deductStock(request.getProductId(), request.getQuantity());

        // 步骤2：创建订单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setAmount(BigDecimal.valueOf(request.getQuantity() * 100L));
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.insert(order);

        // 步骤3：写 outbox 消息（与步骤1、2同一DB事务 ← 这是 Outbox Pattern 的关键！）
        // 此时消息还没发到 MQ，只是在 outbox 表里记了一条 PENDING 的记录。
        // Scheduler 会异步扫描这条记录并发到 MQ。
        outboxMessageRepository.insert(buildOutboxMessage(orderNo, request));

        log.info("订单创建成功，outbox消息已写入，等待Scheduler投递: orderNo={}", orderNo);
        return orderNo;
    }

    /**
     * 构建 outbox 消息，payload 存储完整的事件快照（而非ID引用）。
     * 这样消费端无需回查 order-service，消息是自包含的，解耦性更好。
     */
    private OutboxMessage buildOutboxMessage(String orderNo, CreateOrderRequest request) {
        String messageId = orderNo + "-" + System.currentTimeMillis();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .messageId(messageId)
                .orderNo(orderNo)
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .amount(BigDecimal.valueOf(request.getQuantity() * 100L))
                .createdAt(LocalDateTime.now())
                .build();

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("序列化 OrderCreatedEvent 失败", e);
        }

        OutboxMessage msg = new OutboxMessage();
        msg.setMessageId(messageId);
        msg.setAggregateType("ORDER");
        msg.setAggregateId(orderNo);
        msg.setTopic("order-created");
        msg.setPayload(payload);
        msg.setStatus("PENDING");
        msg.setRetryCount(0);
        msg.setMaxRetry(5);
        msg.setNextRetryAt(LocalDateTime.now());
        msg.setCreatedAt(LocalDateTime.now());
        return msg;
    }
}
