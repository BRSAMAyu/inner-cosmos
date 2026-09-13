package com.innercosmos.vo;

import com.innercosmos.entity.SlowLetter;

/**
 * CP-33 sender-facing letter summary. Receipt privacy by design: the sender never learns
 * WHICH way a letter ended — DECLINED and BLOCKED both collapse to CLOSED (a rejection
 * must not reveal that the recipient blocked the sender), and the letter body is not
 * echoed back in list responses once sent. The sender's own DRAFT body IS included: an
 * unsent draft is the sender's composing surface, nothing of the recipient's. CP-33 §2-6
 * additionally masks the read state through the recipient's own opt-in:
 * SlowLetterServiceImpl#outbox already collapses READ -> DELIVERED (and strips
 * readAt/receiptPolicy) while the recipient's policy is not ALWAYS, so this mapping
 * deliberately does NOT re-apply that rule — re-masking an already-masked view would read
 * the stripped policy as NEVER and wrongly hide an opted-in READ receipt.
 */
public class SlowLetterOutboxVO {
    public Long id;
    public String title;
    /** DRAFT/SENT/FLYING/DELIVERED/READ/REPLIED/ARCHIVED as-is; DECLINED|BLOCKED -> CLOSED. */
    public String senderStatus;
    public String statusExplanation;
    /** Present on DRAFT rows only (the sender's own composing text). */
    public String letterBody;
    public String createdAt;
    public String scheduledArrivalAt;

    public static SlowLetterOutboxVO from(SlowLetter letter) {
        SlowLetterOutboxVO vo = new SlowLetterOutboxVO();
        vo.id = letter.id;
        vo.title = letter.title;
        boolean closed = "DECLINED".equals(letter.status) || "BLOCKED".equals(letter.status);
        vo.senderStatus = closed ? "CLOSED" : letter.status;
        vo.statusExplanation = closed
                ? "这封信已结束，未能继续往来。"
                : "SENT/FLYING=在途（到达时刻见 scheduledArrivalAt）；DELIVERED=对方可读取；"
                        + "READ/REPLIED 由对方的回执选择决定。";
        if ("DRAFT".equals(letter.status)) {
            vo.letterBody = letter.letterBody;
        }
        vo.createdAt = letter.createdAt == null ? null : letter.createdAt.toString();
        vo.scheduledArrivalAt = letter.scheduledArrivalAt == null
                ? null : letter.scheduledArrivalAt.toString();
        return vo;
    }
}
