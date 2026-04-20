package com.example.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.domain.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryRepository extends BaseMapper<Inventory> {

    // 原子扣减：WHERE available_stock >= quantity 防止超卖，返回受影响行数
    @Update("UPDATE inventory SET available_stock = available_stock - #{quantity} " +
            "WHERE product_id = #{productId} AND available_stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}
