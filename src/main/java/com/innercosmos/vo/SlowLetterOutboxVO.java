package com.innercosmos.vo;

import com.innercosmos.entity.SlowLetter;

/**
 * CP-33 sender-facing letter summary. Receipt privacy by design: the sender never learns
 * WHICH way a letter ended — DECLINED and BLOCKED both collapse to CLOSED (a rejection
 * must not reveal that the recipient blocked the sender), and the letter body is not
 * echoed back in list responses.
 */
public class SlowLetterOutboxVO {
    public Long id;
    public String title;
    /** DRAFT/SENT/FLYING/DELIVERED/READ/REPLIED/ARCHIVED as-is; DECLINED|BLOCKED -> CLOSED. */
    public String senderStatus;
    public String statusExplanation;
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
        vo.createdAt = letter.createdAt == null ? null : letter.createdAt.toString();
        vo.scheduledArrivalAt = letter.scheduledArrivalAt == null
                ? null : letter.scheduledArrivalAt.toString();
        return vo;
    }
}
