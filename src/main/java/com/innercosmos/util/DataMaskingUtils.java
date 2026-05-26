package com.innercosmos.util;

public final class DataMaskingUtils {
    private DataMaskingUtils() {
    }

    public static String maskContact(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\d{6,}", "[数字已脱敏]")
                .replaceAll("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}", "[邮箱已脱敏]");
    }
}
