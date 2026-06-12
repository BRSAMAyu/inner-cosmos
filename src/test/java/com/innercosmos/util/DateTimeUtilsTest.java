package com.innercosmos.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateTimeUtilsTest {

    // format with a valid LocalDateTime returns formatted string
    @Test
    void formatValidDateTimeReturnsFormattedString() {
        LocalDateTime dt = LocalDateTime.of(2026, Month.JUNE, 12, 14, 30, 45);
        String result = DateTimeUtils.format(dt);

        assertNotNull(result);
        assertEquals("2026-06-12 14:30:45", result);
    }

    // format with null returns empty string (actual API behaviour)
    @Test
    void formatNullReturnsEmpty() {
        assertEquals("", DateTimeUtils.format(null));
    }

    // format output matches the expected pattern
    @Test
    void formatMatchesExpectedPattern() {
        LocalDateTime dt = LocalDateTime.of(2025, Month.JANUARY, 1, 0, 0, 0);
        String result = DateTimeUtils.format(dt);

        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "Expected yyyy-MM-dd HH:mm:ss but was " + result);
    }

    // format handles different dates correctly
    @Test
    void formatHandlesDifferentDates() {
        LocalDateTime newYear = LocalDateTime.of(2024, Month.JANUARY, 1, 0, 0, 1);
        LocalDateTime leapDay = LocalDateTime.of(2024, Month.FEBRUARY, 29, 23, 59, 59);
        LocalDateTime endOfYear = LocalDateTime.of(2023, Month.DECEMBER, 31, 12, 0, 0);

        assertEquals("2024-01-01 00:00:01", DateTimeUtils.format(newYear));
        assertEquals("2024-02-29 23:59:59", DateTimeUtils.format(leapDay));
        assertEquals("2023-12-31 12:00:00", DateTimeUtils.format(endOfYear));
    }

    // format with midnight time shows zero-padded values
    @Test
    void formatPadsWithZeros() {
        LocalDateTime dt = LocalDateTime.of(2026, Month.MARCH, 5, 3, 4, 9);
        String result = DateTimeUtils.format(dt);

        assertEquals("2026-03-05 03:04:09", result);
    }

    // format is deterministic across calls with same input
    @Test
    void formatIsDeterministic() {
        LocalDateTime dt = LocalDateTime.of(2026, Month.JUNE, 12, 14, 30, 45);
        String first = DateTimeUtils.format(dt);
        String second = DateTimeUtils.format(dt);

        assertEquals(first, second);
    }
}