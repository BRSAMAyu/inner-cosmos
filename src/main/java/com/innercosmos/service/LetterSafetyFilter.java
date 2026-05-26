package com.innercosmos.service;

public interface LetterSafetyFilter {
    FilterResult filter(String letterBody, Long senderId, Long receiverId);

    class FilterResult {
        public boolean passed;
        public String reason;
        public String suggestion;
    }
}
