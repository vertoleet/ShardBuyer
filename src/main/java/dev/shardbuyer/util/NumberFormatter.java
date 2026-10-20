package dev.shardbuyer.util;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Форматирование чисел:
 * FULL  — 1 000 000 / 1,000,000 / 1.000.000 / 1000000 (+ дробная часть)
 * SHORT — 1k / 1.5m / 2b ...
 */
public final class NumberFormatter {

    public enum Mode { FULL, SHORT }

    private final Mode mode;
    private final String groupSeparator;
    private final String decimalSeparator;
    private final int decimals;
    private final boolean trimZeros;
    private final int shortDecimals;
    private final double shortMin;
    private final List<String> suffixes;

    public NumberFormatter(Mode mode, String groupSeparator, String decimalSeparator, int decimals, boolean trimZeros,
                           int shortDecimals, double shortMin, List<String> suffixes) {
        this.mode = mode;
        this.groupSeparator = groupSeparator;
        this.decimalSeparator = decimalSeparator;
        this.decimals = Math.max(0, Math.min(8, decimals));
        this.trimZeros = trimZeros;
        this.shortDecimals = Math.max(0, Math.min(6, shortDecimals));
        this.shortMin = shortMin;
        this.suffixes = suffixes.isEmpty() ? List.of("", "k", "m", "b", "t", "q") : suffixes;
    }

    public static NumberFormatter from(ConfigurationSection s, int defaultDecimals) {
        if (s == null) {
            return new NumberFormatter(Mode.FULL, " ", ".", defaultDecimals, true, 1, 1000, List.of());
        }
        Mode mode;
        try {
            mode = Mode.valueOf(s.getString("mode", "FULL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = Mode.FULL;
        }
        String group = separator(s.getString("thousands-separator", "SPACE"));
        String decRaw = s.getString("decimal-separator", "AUTO").toUpperCase(Locale.ROOT);
        String dec;
        switch (decRaw) {
            case "DOT" -> dec = ".";
            case "COMMA" -> dec = ",";
            case "AUTO" -> dec = ",".equals(group) ? "." : (".".equals(group) ? "," : ".");
            default -> dec = s.getString("decimal-separator", ".");
        }
        List<String> suffixes = new ArrayList<>(s.getStringList("short.suffixes"));
        return new NumberFormatter(mode, group, dec,
                s.getInt("decimals", defaultDecimals),
                s.getBoolean("trim-zeros", true),
                s.getInt("short.decimals", 1),
                s.getDouble("short.min-value", 1000),
                suffixes);
    }

    private static String separator(String raw) {
        if (raw == null) {
            return " ";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "SPACE" -> " ";
            case "COMMA" -> ",";
            case "DOT" -> ".";
            case "NONE", "" -> "";
            case "UNDERSCORE" -> "_";
            case "APOSTROPHE" -> "'";
            default -> raw;
        };
    }

    public String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        if (mode == Mode.SHORT && Math.abs(value) >= shortMin) {
            return formatShort(value);
        }
        return formatFull(value, decimals, trimZeros);
    }

    private String formatShort(double value) {
        double abs = Math.abs(value);
        int idx = 0;
        while (abs >= 1000 && idx < suffixes.size() - 1) {
            abs /= 1000;
            idx++;
        }
        BigDecimal bd = BigDecimal.valueOf(abs).setScale(shortDecimals, RoundingMode.HALF_UP);
        if (bd.compareTo(BigDecimal.valueOf(1000)) >= 0 && idx < suffixes.size() - 1) {
            abs = bd.doubleValue() / 1000;
            idx++;
        }
        String num = formatFull(abs, shortDecimals, true);
        return (value < 0 ? "-" : "") + num + suffixes.get(idx);
    }

    private String formatFull(double value, int scale, boolean trim) {
        BigDecimal bd = BigDecimal.valueOf(Math.abs(value)).setScale(scale, RoundingMode.HALF_UP);
        String plain = bd.toPlainString();
        String intPart = plain;
        String frac = null;
        int dot = plain.indexOf('.');
        if (dot >= 0) {
            intPart = plain.substring(0, dot);
            frac = plain.substring(dot + 1);
        }
        if (frac != null && trim) {
            int end = frac.length();
            while (end > 0 && frac.charAt(end - 1) == '0') {
                end--;
            }
            frac = end == 0 ? null : frac.substring(0, end);
        }
        StringBuilder grouped = new StringBuilder();
        int len = intPart.length();
        for (int i = 0; i < len; i++) {
            grouped.append(intPart.charAt(i));
            int left = len - i - 1;
            if (left > 0 && left % 3 == 0) {
                grouped.append(groupSeparator);
            }
        }
        boolean negative = value < 0 && (bd.signum() != 0);
        return (negative ? "-" : "") + grouped + (frac != null ? decimalSeparator + frac : "");
    }
}
