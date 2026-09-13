package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * CP-18 closing-checklist §2-13: the owner's per-user withdrawal switch for VISIBLE
 * cross-session continuity (the opening card). One row per user; absence of a row means
 * the default OPEN state. Turning visibility off is a DISPLAY choice only — continuity
 * facts (session summaries, memory settlement) keep being recorded honestly elsewhere.
 */
@TableName("tb_continuity_preference")
public class ContinuityPreference extends BaseEntity {
    public Long userId;
    /** False = the opening supply endpoint answers with an explicit withdrawn marker. */
    public Boolean openingVisible;
}
