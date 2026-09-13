package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_social_group_member")
public class SocialGroupMember extends BaseEntity {
    public Long groupId;
    public Long userId;
    public String memberRole;
    public String status;
    /**
     * CP-35 (V50): the instant this membership became ACTIVE. The row itself is created at
     * invite time (status PENDING), so {@code createdAt} is the invite instant and would leak
     * pre-join history; {@code joinedAt} is the history-visibility boundary for ordinary
     * members. Kept on the same wall-clock source as MybatisMetaObjectHandler's
     * {@code createdAt} fill (LocalDateTime.now()) so the SQL comparison is zone-consistent.
     */
    public LocalDateTime joinedAt;
    /** NULL = not muted; set + {@code mutedUntil} NULL = muted until manual release (V50). */
    public LocalDateTime mutedAt;
    public LocalDateTime mutedUntil;
    public Long mutedBy;
}
