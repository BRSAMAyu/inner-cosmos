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

    /**
     * IC-CAP-002 B-1: find an existing queue row for a (user, capsule) pair in a given
     * status. Used to dedupe PENDING rows so multiple portrait/memory triggers update the
     * same row instead of storming the queue with duplicates.
     */
    @Select("SELECT * FROM tb_capsule_sync_queue WHERE user_id = #{userId} AND capsule_id = #{capsuleId} "
            + "AND status = #{status} ORDER BY created_at DESC LIMIT 1")
    CapsuleSyncQueue findByUserCapsuleAndStatus(@Param("userId") Long userId,
                                                @Param("capsuleId") Long capsuleId,
                                                @Param("status") String status);

    /**
     * IC-CAP-002 B-1: returns PENDING + FAILED rows so the user can see both unprocessed
     * proposals and sync failures that need attention.
     */
    @Select("SELECT * FROM tb_capsule_sync_queue WHERE user_id = #{userId} "
            + "AND status IN ('PENDING','FAILED') ORDER BY created_at DESC")
    List<CapsuleSyncQueue> findPendingOrFailed(@Param("userId") Long userId);

    /**
     * IC-CAP-002 B-2: retry sweep — FAILED rows whose backoff has elapsed and that have
     * not yet exhausted their attempt budget.
     */
    @Select("SELECT * FROM tb_capsule_sync_queue WHERE status = 'FAILED' "
            + "AND (next_retry_at IS NULL OR next_retry_at <= #{now}) "
            + "AND (attempt_count IS NULL OR attempt_count < #{maxAttempts}) ORDER BY created_at ASC")
    List<CapsuleSyncQueue> findRetryable(@Param("now") java.time.LocalDateTime now,
                                         @Param("maxAttempts") int maxAttempts);
}