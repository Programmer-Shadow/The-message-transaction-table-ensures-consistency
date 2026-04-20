package com.example.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.domain.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderRepository extends BaseMapper<Order> {
}
