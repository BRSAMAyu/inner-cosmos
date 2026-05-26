package com.innercosmos.util;

public final class TokenEstimateUtils {
    private TokenEstimateUtils() {
    }

    public static int estimate(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 2);
    }
}
