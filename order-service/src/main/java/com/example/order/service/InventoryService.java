package com.example.order.service;

import com.example.order.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    // MANDATORY：必须在外部事务中调用，保证与订单写入同一事务
    @Transactional(propagation = Propagation.MANDATORY)
    public void deductStock(Long productId, int quantity) {
        int affected = inventoryRepository.deductStock(productId, quantity);
        if (affected == 0) {
            throw new InsufficientStockException(
                    "库存不足，productId=" + productId + ", 需要=" + quantity);
        }
    }

    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }
}
