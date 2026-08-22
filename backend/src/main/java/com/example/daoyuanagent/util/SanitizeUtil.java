package com.example.daoyuanagent.util;

public class SanitizeUtil {
    public static String sanitizeInput(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                    .trim();
    }

    public static String maskPhone(String text) {
        if (text == null) return "";
        return text.replaceAll("1[3-9]\\d{9}", "***手机号***");
    }

    public static String maskIdCard(String text) {
        if (text == null) return "";
        return text.replaceAll("\\d{17}[\\dXx]", "***身份证号***");
    }

    public static String maskSensitive(String text) {
        if (text == null) return "";
        return maskIdCard(maskPhone(text));
    }
}
