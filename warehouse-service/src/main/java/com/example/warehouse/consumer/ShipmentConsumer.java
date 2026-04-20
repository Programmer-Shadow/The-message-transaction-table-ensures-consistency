package com.example.warehouse.consumer;

import com.example.warehouse.dto.OrderCreatedEvent;
import com.example.warehouse.service.IdempotentService;
import com.example.warehouse.service.ShipmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "order-created", consumerGroup = "warehouse-consumer-group")
public class ShipmentConsumer implements RocketMQListener<MessageExt> {

    private final IdempotentService idempotentService;
    private final ShipmentService shipmentService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt message) {
        // messageId 由生产端通过 KEYS header 传递，用于消费端幂等
        String messageId = message.getKeys();
        String body = new String(message.getBody());

        log.info("收到订单消息: messageId={}", messageId);

        try {
            OrderCreatedEvent event = objectMapper.readValue(body, OrderCreatedEvent.class);
            idempotentService.executeIdempotent(messageId, () ->
                    shipmentService.createShipment(
                            event.getOrderNo(),
                            event.getProductId(),
                            event.getQuantity()
                    )
            );
        } catch (Exception e) {
            log.error("消息处理异常，触发MQ重试: messageId={}, error={}", messageId, e.getMessage());
            // 抛出异常让 RocketMQ 触发重试（受 max-reconsume-times 控制）
            throw new RuntimeException(e);
        }
    }
}
