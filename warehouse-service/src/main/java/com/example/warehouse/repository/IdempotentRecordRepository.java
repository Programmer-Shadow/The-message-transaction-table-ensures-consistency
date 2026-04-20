package com.example.warehouse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.warehouse.domain.IdempotentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdempotentRecordRepository extends BaseMapper<IdempotentRecord> {

    @Update("UPDATE idempotent_record SET status = #{status}, updated_at = NOW() " +
            "WHERE message_id = #{messageId}")
    int updateStatus(@Param("messageId") String messageId, @Param("status") String status);
}
