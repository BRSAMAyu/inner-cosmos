package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * IC-CAP-002 B-3: lightweight system notification (e.g. capsule sync done/failed).
 * Distinct from tb_slow_letter — slow letters carry a "delivery ritual" semantic,
 * system notifications are a different object (Spec D3).
 *
 * id / createdAt / updatedAt come from {@link BaseEntity}.
 */
@TableName("tb_notification")
public class Notification extends BaseEntity {
    public Long userId;
    public String type;     // e.g. SYNC_DONE, SYNC_FAILED
    public String title;
    public String body;
    public Long refId;      // related entity id (e.g. sync queue id)
    public String refType;  // related entity type (e.g. CAPSULE_SYNC)

    // "read" is reserved-ish in some DBs; map to is_read column for MySQL safety.
    @TableField("is_read")
    public Boolean read;
}
