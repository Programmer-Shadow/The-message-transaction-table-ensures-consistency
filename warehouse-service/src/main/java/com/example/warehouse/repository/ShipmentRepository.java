package com.example.warehouse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.warehouse.domain.Shipment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShipmentRepository extends BaseMapper<Shipment> {
}
