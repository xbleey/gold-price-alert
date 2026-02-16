package com.xbleey.goldpricealert.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoldAlertLevelName {

    private static final Pattern LEVEL_PATTERN = Pattern.compile("^P([1-9][0-9]*)$");

    private GoldAlertLevelName() {
    }

    public static String normalize(String levelName) {
        if (levelName == null) {
            return null;
        }
        return levelName.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String levelName) {
        String normalized = normalize(levelName);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return LEVEL_PATTERN.matcher(normalized).matches();
    }

    public static int rankOf(String levelName) {
        String normalized = normalize(levelName);
        Matcher matcher = LEVEL_PATTERN.matcher(normalized == null ? "" : normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid levelName, expected P<number>: " + levelName);
        }
        return Integer.parseInt(matcher.group(1));
    }

    public static boolean isProtectedLevel(String levelName) {
        return rankOf(levelName) <= 5;
    }
}
