package com.innercosmos.vo;

/**
 * IC-CAP-001: authoritative per-capsule per-day quota state for a visitor.
 *
 * This VO is the single source of truth surfaced to the frontend (and any other
 * client) for "how many turns remain today on this capsule". The backend quota
 * table (tb_capsule_usage_quota) is authoritative; this VO is its read projection.
 */
public class CapsuleQuotaVO {
    /** Number of turns the visitor has already used today on this capsule. */
    public int turnCount;
    /** Effective daily turn limit for this capsule (SEED=50, otherwise clamped configured value). */
    public int dailyLimit;
    /** Remaining turns today (>= 0). */
    public int remaining;
    /** True when this runtime intentionally does not enforce a usage quota. */
    public boolean unlimited;
    /** Whether this capsule is an official SEED capsule. */
    public boolean seed;
    /** The quota date (today) in ISO format (yyyy-MM-dd). */
    public String quotaDate;

    public CapsuleQuotaVO() {}

    public CapsuleQuotaVO(int turnCount, int dailyLimit, int remaining, boolean seed, String quotaDate) {
        this(turnCount, dailyLimit, remaining, seed, quotaDate, false);
    }

    public CapsuleQuotaVO(int turnCount, int dailyLimit, int remaining, boolean seed, String quotaDate,
                          boolean unlimited) {
        this.turnCount = turnCount;
        this.dailyLimit = dailyLimit;
        this.remaining = remaining;
        this.seed = seed;
        this.quotaDate = quotaDate;
        this.unlimited = unlimited;
    }

    public int getTurnCount() { return turnCount; }
    public void setTurnCount(int turnCount) { this.turnCount = turnCount; }

    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }

    public int getRemaining() { return remaining; }
    public void setRemaining(int remaining) { this.remaining = remaining; }

    public boolean isUnlimited() { return unlimited; }
    public void setUnlimited(boolean unlimited) { this.unlimited = unlimited; }

    public boolean isSeed() { return seed; }
    public void setSeed(boolean seed) { this.seed = seed; }

    public String getQuotaDate() { return quotaDate; }
    public void setQuotaDate(String quotaDate) { this.quotaDate = quotaDate; }
}
