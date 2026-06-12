package com.innercosmos.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSanitizerTest {

    // compact trims leading and trailing whitespace
    @Test
    void compactTrimsLeadingAndTrailingWhitespace() {
        assertEquals("hello world", TextSanitizer.compact("   hello world   "));
    }

    // compact collapses multiple spaces into a single space
    @Test
    void compactCollapsesMultipleSpaces() {
        assertEquals("hello world", TextSanitizer.compact("hello     world"));
    }

    // compact handles tabs and newlines
    @Test
    void compactHandlesTabsAndNewlines() {
        assertEquals("a b c d", TextSanitizer.compact("a\tb\nc\r\nd"));
    }

    // compact with null returns empty string (actual API behaviour)
    @Test
    void compactWithNullReturnsEmpty() {
        assertEquals("", TextSanitizer.compact(null));
    }

    // compact with empty returns empty
    @Test
    void compactWithEmptyReturnsEmpty() {
        assertEquals("", TextSanitizer.compact(""));
    }

    // compact preserves single spaces within content
    @Test
    void compactPreservesSingleSpaces() {
        assertEquals("one two three", TextSanitizer.compact("one two three"));
    }

    // compact handles mixed whitespace combinations
    @Test
    void compactHandlesMixedWhitespace() {
        assertEquals("x y", TextSanitizer.compact(" \t x \n  y \r\n "));
    }

    // compact with whitespace-only input returns empty
    @Test
    void compactWhitespaceOnlyReturnsEmpty() {
        assertEquals("", TextSanitizer.compact("   \t\n  "));
    }

    // compact is idempotent
    @Test
    void compactIsIdempotent() {
        String first = TextSanitizer.compact("  hello   world  ");
        String second = TextSanitizer.compact(first);
        assertEquals(first, second);
    }

    // compact does not touch internal punctuation
    @Test
    void compactDoesNotTouchPunctuation() {
        assertEquals("hello, world!", TextSanitizer.compact("  hello,  world!  "));
    }
}