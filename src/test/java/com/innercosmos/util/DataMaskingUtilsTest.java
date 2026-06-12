package com.innercosmos.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataMaskingUtilsTest {

    // maskContact with a phone-like digit sequence masks it
    @Test
    void maskContactMasksPhoneLikeDigits() {
        String input = "Call me at 13800138000 today";
        String masked = DataMaskingUtils.maskContact(input);

        assertNotNull(masked);
        assertTrue(masked.contains("[数字已脱敏]"));
        assertTrue(!masked.contains("13800138000"));
        assertTrue(masked.startsWith("Call me at "));
        assertTrue(masked.endsWith(" today"));
    }

    // maskContact with an email masks the email
    @Test
    void maskContactMasksEmail() {
        String input = "Contact: alice.smith@example.com please";
        String masked = DataMaskingUtils.maskContact(input);

        assertNotNull(masked);
        assertTrue(masked.contains("[邮箱已脱敏]"));
        assertTrue(!masked.contains("alice.smith@example.com"));
    }

    // maskContact with both phone-like digits and email
    @Test
    void maskContactMasksBothDigitsAndEmail() {
        String input = "phone=13912345678 mail=bob@example.org";
        String masked = DataMaskingUtils.maskContact(input);

        assertNotNull(masked);
        assertTrue(masked.contains("[数字已脱敏]"));
        assertTrue(masked.contains("[邮箱已脱敏]"));
    }

    // maskContact with null returns empty string
    @Test
    void maskContactWithNullReturnsEmpty() {
        assertEquals("", DataMaskingUtils.maskContact(null));
    }

    // maskContact with short digit run (less than 6) leaves it alone
    @Test
    void maskContactWithShortDigitsLeavesUnchanged() {
        String input = "Order 12345 is small";
        String masked = DataMaskingUtils.maskContact(input);

        // 5 digits (< 6) should not be masked
        assertEquals(input, masked);
    }

    // maskContact with plain text returns text unchanged
    @Test
    void maskContactWithPlainTextUnchanged() {
        String input = "Hello world";
        assertEquals(input, DataMaskingUtils.maskContact(input));
    }

    // maskContact with empty string returns empty
    @Test
    void maskContactWithEmptyReturnsEmpty() {
        assertEquals("", DataMaskingUtils.maskContact(""));
    }

    // maskContact handles exactly six digits
    @Test
    void maskContactWithSixDigitsMasks() {
        String input = "code 123456 done";
        String masked = DataMaskingUtils.maskContact(input);

        assertTrue(masked.contains("[数字已脱敏]"));
        assertTrue(!masked.contains("123456"));
    }

    // maskContact masks phone-shaped strings of various lengths
    @Test
    void maskContactMasksPhoneOfVariousLengths() {
        String input = "1111111111";
        String masked = DataMaskingUtils.maskContact(input);

        assertEquals("[数字已脱敏]", masked);
    }

    // maskContact handles subaddress emails with plus sign
    @Test
    void maskContactMasksEmailWithPlusSuffix() {
        String input = "send to user+tag@example.com now";
        String masked = DataMaskingUtils.maskContact(input);

        assertTrue(masked.contains("[邮箱已脱敏]"));
    }
}