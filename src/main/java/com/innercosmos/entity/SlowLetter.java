package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.innercosmos.config.json.UtcLocalDateTimeSerializer;
import java.time.LocalDateTime;

@TableName("tb_slow_letter")
public class SlowLetter extends BaseEntity {
    public Long senderUserId;
    public Long receiverUserId;
    public Long receiverCapsuleId;
    public Long threadId;
    public String title;
    public String letterBody;
    public String status;
    public Integer parallaxDistance;
    /** Server-normalized cadence selected by the sender (see LetterDeliveryPreset). */
    public String deliveryPreset;
    /** IANA timezone retained as delivery intent for TONIGHT/TOMORROW. */
    public String deliveryTimeZone;
    /** Server-authoritative scheduled arrival fixed when the draft is actually sent. */
    @JsonSerialize(using = UtcLocalDateTimeSerializer.class)
    public LocalDateTime scheduledArrivalAt;
    @JsonSerialize(using = UtcLocalDateTimeSerializer.class)
    public LocalDateTime estimatedArrivalAt;
    @JsonSerialize(using = UtcLocalDateTimeSerializer.class)
    public LocalDateTime sentAt;
    @JsonSerialize(using = UtcLocalDateTimeSerializer.class)
    public LocalDateTime deliveredAt;
    @JsonSerialize(using = UtcLocalDateTimeSerializer.class)
    public LocalDateTime readAt;
    @JsonSerialize(using = UtcLocalDateTimeSerializer.class)
    public LocalDateTime repliedAt;
    /**
     * Gemini audit 1.8 (CONFIRMED/P1): set on a reply letter to the id of the letter it replies
     * to. Lets a reply's own SENT transition atomically flip the ORIGINAL to REPLIED as a
     * reliable side effect of actually sending -- never optimistically at draft-creation time.
     */
    public Long replyToLetterId;
    /** Gemini audit 1.8: optimistic-concurrency version for the owner-scoped draft PATCH. */
    public Integer versionNo;
    /**
     * Gemini audit 1.8: client-supplied idempotency key for compose actions (draft/reply-with-
     * letter). A retried create with the same (sender, key) returns the original row instead of
     * inserting a duplicate letter.
     */
    public String idempotencyKey;
}
