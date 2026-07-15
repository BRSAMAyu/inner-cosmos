package com.innercosmos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innercosmos.entity.MemoryEmbedding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemoryEmbeddingMapper extends BaseMapper<MemoryEmbedding> {
    @Select("""
            SELECT c.id
            FROM tb_memory_card c
            WHERE c.status = 'ACTIVE'
              AND UPPER(COALESCE(c.consent_scope, '')) NOT IN ('LOCAL_ONLY', 'NO_EXTERNAL_PROCESSING')
              AND NOT EXISTS (
                SELECT 1 FROM tb_memory_embedding e
                WHERE e.memory_id = c.id
                  AND e.user_id = c.user_id
                  AND e.model_name = #{modelName}
                  AND e.model_version = #{modelVersion}
                  AND e.source_version = COALESCE(c.version_no, 1)
                  AND e.task_scope = 'GENERAL'
                  AND e.status = 'ACTIVE'
              )
            ORDER BY c.id
            LIMIT #{limit}
            """)
    List<Long> selectMissingMemoryIds(@Param("modelName") String modelName,
                                      @Param("modelVersion") String modelVersion,
                                      @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM tb_memory_card c
            WHERE c.status = 'ACTIVE'
              AND UPPER(COALESCE(c.consent_scope, '')) NOT IN ('LOCAL_ONLY', 'NO_EXTERNAL_PROCESSING')
              AND NOT EXISTS (
                SELECT 1 FROM tb_memory_embedding e
                WHERE e.memory_id = c.id
                  AND e.user_id = c.user_id
                  AND e.model_name = #{modelName}
                  AND e.model_version = #{modelVersion}
                  AND e.source_version = COALESCE(c.version_no, 1)
                  AND e.task_scope = 'GENERAL'
                  AND e.status = 'ACTIVE'
              )
            """)
    long countMissing(@Param("modelName") String modelName,
                      @Param("modelVersion") String modelVersion);
}
