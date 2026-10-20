package dev.shardbuyer.util;

import dev.shardbuyer.config.Messages;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Item names.
 * <ul>
 *   <li>In chat, item names are sent as translatable text — every player sees them in their client language.</li>
 *   <li>item-names.yml can override the name of any item (then that text is used everywhere).</li>
 * </ul>
 */
public final class Names {

    private static final int[] ROMAN_V = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_S = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private final Map<String, String> items = new HashMap<>();

    public void loadItems(ConfigurationSection s) {
        items.clear();
        if (s == null) {
            return;
        }
        for (String k : s.getKeys(false)) {
            String v = s.getString(k);
            if (v != null && !v.isEmpty()) {
                items.put(k.toUpperCase(Locale.ROOT), v);
            }
        }
    }

    /** Plain text name: custom name or English name. */
    public String item(Material m) {
        if (m == null) {
            return "?";
        }
        String n = items.get(m.name());
        return n != null ? Text.color(n) : pretty(m.name());
    }

    /** Name for chat messages: custom name, or a translatable name (client language). */
    public Object itemArg(Material m) {
        if (m == null) {
            return "?";
        }
        String n = items.get(m.name());
        if (n != null) {
            return Text.color(n);
        }
        try {
            return new Messages.Tr(m.translationKey(), pretty(m.name()));
        } catch (Throwable t) {
            return pretty(m.name());
        }
    }

    public String enchant(Enchantment e) {
        return pretty(e.getKey().getKey());
    }

    /** Search: by id, English name or custom name. */
    public boolean matches(Material m, String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            return true;
        }
        if (m.name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(q.replace('_', ' '))) {
            return true;
        }
        String n = items.get(m.name());
        return n != null && Text.strip(Text.color(n)).toLowerCase(Locale.ROOT).contains(q);
    }

    public boolean matches(Enchantment e, String query) {
        String q = query.toLowerCase(Locale.ROOT).trim().replace('_', ' ');
        return e.getKey().getKey().replace('_', ' ').contains(q);
    }

    public static String pretty(String id) {
        String[] parts = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static String roman(int n) {
        if (n <= 0 || n > 3999) {
            return String.valueOf(n);
        }
        StringBuilder sb = new StringBuilder();
        int v = n;
        for (int i = 0; i < ROMAN_V.length; i++) {
            while (v >= ROMAN_V[i]) {
                v -= ROMAN_V[i];
                sb.append(ROMAN_S[i]);
            }
        }
        return sb.toString();
    }
}
