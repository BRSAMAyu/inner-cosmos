package com.innercosmos.dto;

/**
 * The sender's chosen slow-letter cadence.
 *
 * <p>The two DEMO presets deliberately accelerate only the time scale. They still create a real
 * SENT -> FLYING -> DELIVERED journey, driven by the server-side scheduler; they never fabricate
 * a delivered state on the client.</p>
 */
public enum LetterDeliveryPreset {
    DEMO_30S,
    DEMO_3M,
    TONIGHT,
    TOMORROW,
    CUSTOM
}
