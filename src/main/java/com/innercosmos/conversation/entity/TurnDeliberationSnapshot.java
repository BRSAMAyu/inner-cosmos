package com.innercosmos.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.innercosmos.entity.BaseEntity;

/**
 * Versioned, user-safe planner decision snapshot. snapshotJson must contain only the bounded
 * DeliberationPlan contract and never hidden chain-of-thought or raw provider reasoning.
 */
@TableName("tb_turn_deliberation_snapshot")
public class TurnDeliberationSnapshot extends BaseEntity {
    public Long turnId;
    public Long userId;
    public Integer planRevision;
    public String status;
    public String snapshotJson;
}
