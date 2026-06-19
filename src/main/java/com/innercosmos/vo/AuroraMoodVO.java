package com.innercosmos.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * IC-EMO-002 — the real-time "此刻情绪" payload for the Aurora mood energy-orb on
 * aurora-chat. Sourced from the same latest-enriched-trace read the prompt uses
 * ({@link com.innercosmos.service.EmotionInsightService#latestMood}).
 *
 * <p>Always well-formed: when the user has no trace (or has opted out of
 * emotion/weather perception), {@link #present} is {@code false}, the values are
 * neutral/empty, and {@link #gentleLabel} carries a soft sentence — the endpoint
 * never returns null data or a 500.
 */
public class AuroraMoodVO {

    /** True when a real EmotionTrace backs this read; false = neutral fallback. */
    public boolean present;
    /** Primary emotion name (e.g. "平静"); empty when not present. */
    public String primaryEmotion;
    /** Intensity on the 0..10 scale (0 when not present). */
    public double intensity;
    /** Derived emotion weather (e.g. "SUNNY"); "CLEAR" when neutral. */
    public String weatherType;
    /** Top spectrum entries (emotion + ratio), never null. */
    public List<Entry> spectrum = new ArrayList<>();
    /** A gentle, human label for the orb tooltip / aria-label. Never null. */
    public String gentleLabel;

    /** A single emotion / ratio pair surfaced to the frontend. */
    public static class Entry {
        public String emotion;
        public double ratio;

        public Entry() {
        }

        public Entry(String emotion, double ratio) {
            this.emotion = emotion;
            this.ratio = ratio;
        }
    }
}
