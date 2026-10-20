package dev.shardbuyer.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Работа с цветами (&-коды и HEX &#RRGGBB / <#RRGGBB>) и плейсхолдерами {key}.
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("(?:&#|<#)([A-Fa-f0-9]{6})>?");
    private static final String CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private Text() {
    }

    public static String color(String s) {
        if (s == null || s.isEmpty()) {
            return s == null ? "" : s;
        }
        String input = s;
        if (input.contains("<gradient:")) {
            input = gradients(input);
        }
        if (input.indexOf('#') >= 0) {
            Matcher m = HEX.matcher(input);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String hex = m.group(1);
                StringBuilder rep = new StringBuilder("§x");
                for (char c : hex.toCharArray()) {
                    rep.append('§').append(Character.toLowerCase(c));
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
            }
            m.appendTail(sb);
            input = sb.toString();
        }
        char[] b = input.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && CODES.indexOf(b[i + 1]) > -1) {
                b[i] = '§';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    /**
     * &lt;gradient:#FF0000:#0000FF&gt;text&lt;/gradient&gt; (2+ colors). Formatting codes (&amp;l, &amp;o, &amp;n, &amp;m, &amp;k)
     * inside the text are kept, &amp;r resets them; other color codes inside are ignored.
     */
    private static String gradients(String input) {
        StringBuilder out = new StringBuilder();
        int idx = 0;
        while (true) {
            int start = input.indexOf("<gradient:", idx);
            if (start < 0) {
                out.append(input, idx, input.length());
                break;
            }
            int close = input.indexOf('>', start);
            int end = close < 0 ? -1 : input.indexOf("</gradient>", close);
            if (close < 0 || end < 0) {
                out.append(input, idx, input.length());
                break;
            }
            out.append(input, idx, start);
            String[] colors = input.substring(start + 10, close).split(":");
            List<int[]> rgb = new ArrayList<>();
            for (String c : colors) {
                String h = c.trim().replace("#", "");
                if (h.length() == 6) {
                    try {
                        int v = Integer.parseInt(h, 16);
                        rgb.add(new int[]{(v >> 16) & 255, (v >> 8) & 255, v & 255});
                    } catch (NumberFormatException ignored) {
                        // пропуск
                    }
                }
            }
            String body = input.substring(close + 1, end);
            out.append(rgb.size() < 2 ? body : applyGradient(body, rgb));
            idx = end + "</gradient>".length();
        }
        return out.toString();
    }

    private static String applyGradient(String body, List<int[]> colors) {
        // считаем видимые символы
        int visible = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < body.length() && CODES.indexOf(body.charAt(i + 1)) > -1) {
                i++;
            } else if (c == '') {
                int e = body.indexOf('', i);
                visible++;
                i = e < 0 ? body.length() : e;
            } else {
                visible++;
            }
        }
        StringBuilder sb = new StringBuilder();
        StringBuilder formats = new StringBuilder();
        int n = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < body.length() && CODES.indexOf(body.charAt(i + 1)) > -1) {
                char code = Character.toLowerCase(body.charAt(i + 1));
                if ("klmno".indexOf(code) >= 0) {
                    formats.append('§').append(code);
                } else if (code == 'r') {
                    formats.setLength(0);
                }
                i++;
                continue;
            }
            double t = visible <= 1 ? 0 : (double) n / (visible - 1);
            sb.append(hex(interpolate(colors, t))).append(formats);
            if (c == '') {
                int e = body.indexOf('', i);
                int stop = e < 0 ? body.length() : e + 1;
                sb.append(body, i, stop);
                i = stop - 1;
            } else {
                sb.append(c);
            }
            n++;
        }
        return sb.toString();
    }

    private static int[] interpolate(List<int[]> colors, double t) {
        double scaled = t * (colors.size() - 1);
        int i = Math.min((int) scaled, colors.size() - 2);
        double local = scaled - i;
        int[] a = colors.get(i);
        int[] b = colors.get(i + 1);
        return new int[]{
                (int) Math.round(a[0] + (b[0] - a[0]) * local),
                (int) Math.round(a[1] + (b[1] - a[1]) * local),
                (int) Math.round(a[2] + (b[2] - a[2]) * local)};
    }

    private static String hex(int[] rgb) {
        String h = String.format("%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
        StringBuilder sb = new StringBuilder("§x");
        for (char ch : h.toCharArray()) {
            sb.append('§').append(ch);
        }
        return sb.toString();
    }

    public static List<String> color(List<String> list) {
        List<String> out = new ArrayList<>(list.size());
        for (String s : list) {
            out.add(color(s));
        }
        return out;
    }

    /** Убирает все цветовые коды (§ и &). */
    public static String strip(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < s.length() && CODES.indexOf(s.charAt(i + 1)) > -1) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Подставляет значения вида {key}. Неизвестные плейсхолдеры остаются как есть. */
    public static String replace(String s, Map<String, String> ph) {
        if (s == null || ph == null || ph.isEmpty() || s.indexOf('{') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        int i = 0;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);
            if (c == '{') {
                int j = s.indexOf('}', i + 1);
                if (j > i) {
                    String v = ph.get(s.substring(i + 1, j));
                    if (v != null) {
                        out.append(v);
                        i = j + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
