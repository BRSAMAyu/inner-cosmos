package com.innercosmos.service;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class NaturalTimeNegotiatorTest {
    private final NaturalTimeNegotiator negotiator = new NaturalTimeNegotiator();

    @Test
    void understandsEnglishAmAndPmClockQualifiers() {
        assertThat(negotiator.negotiate("tomorrow at 8:30 pm", ZoneId.of("Asia/Singapore"))
                .preferredAt().toLocalTime().getHour()).isEqualTo(20);
        assertThat(negotiator.negotiate("tomorrow at 12:15 am", ZoneId.of("Asia/Singapore"))
                .preferredAt().toLocalTime().getHour()).isZero();
    }
}
