package com.example.warehouse.service;

import com.example.warehouse.domain.Shipment;
import com.example.warehouse.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    // MANDATORY：必须在 IdempotentService 的外部事务内调用，保证业务与幂等记录同一事务
    @Transactional(propagation = Propagation.MANDATORY)
    public Shipment createShipment(String orderNo, Long productId, Integer quantity) {
        Shipment shipment = new Shipment();
        shipment.setOrderNo(orderNo);
        shipment.setProductId(productId);
        shipment.setQuantity(quantity);
        shipment.setStatus("PENDING");
        shipment.setCreatedAt(LocalDateTime.now());
        shipment.setUpdatedAt(LocalDateTime.now());
        shipmentRepository.insert(shipment);
        log.info("发货单创建成功: orderNo={}", orderNo);
        return shipment;
    }
}
