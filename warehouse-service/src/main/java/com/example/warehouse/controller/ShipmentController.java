package com.example.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.warehouse.domain.Shipment;
import com.example.warehouse.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentRepository shipmentRepository;

    @GetMapping
    public List<Shipment> listShipments() {
        return shipmentRepository.selectList(
                new LambdaQueryWrapper<Shipment>().orderByDesc(Shipment::getCreatedAt).last("LIMIT 20")
        );
    }
}
