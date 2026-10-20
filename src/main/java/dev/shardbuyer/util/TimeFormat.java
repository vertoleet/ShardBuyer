package dev.shardbuyer.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Парсинг ("1d12h", "30m", "90s", "45") и форматирование длительностей. */
public final class TimeFormat {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(ms|d|h|m|s|w)?", Pattern.CASE_INSENSITIVE);

    private final String days;
    private final String hours;
    private final String minutes;
    private final String seconds;
    private final String separator;
    private final int maxUnits;

    public TimeFormat(ConfigurationSection s) {
        this.days = s == null ? "{v}d" : s.getString("days", "{v}d");
        this.hours = s == null ? "{v}h" : s.getString("hours", "{v}h");
        this.minutes = s == null ? "{v}m" : s.getString("minutes", "{v}m");
        this.seconds = s == null ? "{v}s" : s.getString("seconds", "{v}s");
        this.separator = s == null ? " " : s.getString("separator", " ");
        this.maxUnits = s == null ? 3 : Math.max(1, s.getInt("max-units", 3));
    }

    /** @return миллисекунды, или def если строка некорректна */
    public static long parse(String input, long def) {
        if (input == null || input.isBlank()) {
            return def;
        }
        Matcher m = PART.matcher(input.trim().toLowerCase(Locale.ROOT));
        long total = 0;
        boolean found = false;
        while (m.find()) {
            found = true;
            long v = Long.parseLong(m.group(1));
            String unit = m.group(2) == null ? "s" : m.group(2);
            total += switch (unit) {
                case "ms" -> v;
                case "w" -> v * 7L * 24 * 3600_000L;
                case "d" -> v * 24L * 3600_000L;
                case "h" -> v * 3600_000L;
                case "m" -> v * 60_000L;
                default -> v * 1000L;
            };
        }
        return found ? total : def;
    }

    public String format(long millis) {
        long totalSec = Math.max(0, (millis + 999) / 1000);
        long d = totalSec / 86400;
        long h = (totalSec % 86400) / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        List<String> parts = new ArrayList<>();
        if (d > 0) {
            parts.add(days.replace("{v}", String.valueOf(d)));
        }
        if (h > 0) {
            parts.add(hours.replace("{v}", String.valueOf(h)));
        }
        if (m > 0) {
            parts.add(minutes.replace("{v}", String.valueOf(m)));
        }
        if (s > 0 || parts.isEmpty()) {
            parts.add(seconds.replace("{v}", String.valueOf(s)));
        }
        if (parts.size() > maxUnits) {
            parts = parts.subList(0, maxUnits);
        }
        return String.join(separator, parts);
    }
}
