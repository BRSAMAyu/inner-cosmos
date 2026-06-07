package com.innercosmos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innercosmos.entity.CapsuleSyncQueue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CapsuleSyncQueueMapper extends BaseMapper<CapsuleSyncQueue> {

    @Select("SELECT * FROM tb_capsule_sync_queue WHERE user_id = #{userId} AND status = #{status} ORDER BY created_at DESC")
    List<CapsuleSyncQueue> findByUserAndStatus(@Param("userId") Long userId, @Param("status") String status);
}