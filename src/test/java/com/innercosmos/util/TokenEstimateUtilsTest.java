package com.innercosmos.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenEstimateUtilsTest {

    // estimate with normal text returns at least 1 (actual API returns max(1, length/2))
    @Test
    void estimateNormalText() {
        assertEquals(5, TokenEstimateUtils.estimate("abcdefgh")); // length 8, 8/2 = 4, but max(1,4) = 4
        assertEquals(3, TokenEstimateUtils.estimate("abcdef")); // length 6, 6/2 = 3
    }

    // estimate with null returns 0
    @Test
    void estimateNullReturnsZero() {
        assertEquals(0, TokenEstimateUtils.estimate(null));
    }

    // estimate with empty string returns 1 (actual API: max(1, 0/2) = 1)
    @Test
    void estimateEmptyReturnsOne() {
        assertEquals(1, TokenEstimateUtils.estimate(""));
    }

    // estimate with a single char returns 1 (actual API: max(1, 0) = 1, not 0)
    @Test
    void estimateSingleCharReturnsOne() {
        assertEquals(1, TokenEstimateUtils.estimate("a"));
        assertEquals(1, TokenEstimateUtils.estimate("x"));
    }

    // estimate with long text scales roughly with length
    @Test
    void estimateLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("a");
        }
        int estimate = TokenEstimateUtils.estimate(sb.toString());
        // 200 chars / 2 = 100
        assertEquals(100, estimate);
    }

    // estimate with a very long string still scales
    @Test
    void estimateVeryLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("b");
        }
        int estimate = TokenEstimateUtils.estimate(sb.toString());
        assertEquals(5000, estimate);
    }

    // estimate with two chars returns 1 (max(1, 1))
    @Test
    void estimateTwoCharsReturnsOne() {
        assertEquals(1, TokenEstimateUtils.estimate("ab"));
    }

    // estimate value is non-negative for any input
    @Test
    void estimateIsNonNegative() {
        assertTrue(TokenEstimateUtils.estimate(null) >= 0);
        assertTrue(TokenEstimateUtils.estimate("") >= 0);
        assertTrue(TokenEstimateUtils.estimate("a") >= 0);
        assertTrue(TokenEstimateUtils.estimate("hello world this is text") >= 0);
    }
}