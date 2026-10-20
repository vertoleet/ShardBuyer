package dev.shardbuyer.price;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Цены зачарований (enchantments.yml).
 * Цена уровня = base * per-level^(уровень-1), либо явно через levels: {1: 50, 2: 120}.
 */
public final class EnchantPricing {

    private static final class Entry {
        final double base;
        final double perLevel;
        final Map<Integer, Double> levels;

        Entry(double base, double perLevel, Map<Integer, Double> levels) {
            this.base = base;
            this.perLevel = perLevel;
            this.levels = levels;
        }
    }

    private final boolean enabled;
    private final double booksMultiplier;
    private final double itemsMultiplier;
    private final Entry def;
    private final Map<String, Entry> entries = new HashMap<>();

    public EnchantPricing(YamlConfiguration cfg) {
        enabled = cfg.getBoolean("enabled", true);
        booksMultiplier = cfg.getDouble("books-multiplier", 1.0);
        itemsMultiplier = cfg.getDouble("items-multiplier", 1.0);
        def = cfg.isConfigurationSection("default") ? entry(cfg.getConfigurationSection("default")) : null;
        ConfigurationSection s = cfg.getConfigurationSection("enchantments");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                if (s.isConfigurationSection(k)) {
                    entries.put(k.toLowerCase(Locale.ROOT), entry(s.getConfigurationSection(k)));
                } else {
                    entries.put(k.toLowerCase(Locale.ROOT), new Entry(s.getDouble(k), 1.0, Map.of()));
                }
            }
        }
    }

    private static Entry entry(ConfigurationSection s) {
        Map<Integer, Double> levels = new HashMap<>();
        ConfigurationSection l = s.getConfigurationSection("levels");
        if (l != null) {
            for (String k : l.getKeys(false)) {
                try {
                    levels.put(Integer.parseInt(k.trim()), l.getDouble(k));
                } catch (NumberFormatException ignored) {
                    // пропуск
                }
            }
        }
        return new Entry(s.getDouble("base", 0), s.getDouble("per-level", 1.5), levels);
    }

    public boolean enabled() {
        return enabled;
    }

    /** Цена одного зачарования определённого уровня (без множителей книги/предмета). */
    public double value(Enchantment e, int level) {
        if (!enabled || e == null || level <= 0) {
            return 0;
        }
        Entry en = entries.getOrDefault(e.getKey().getKey().toLowerCase(Locale.ROOT), def);
        if (en == null) {
            return 0;
        }
        Double explicit = en.levels.get(level);
        if (explicit != null) {
            return explicit;
        }
        return en.base * Math.pow(en.perLevel, Math.max(0, level - 1));
    }

    public double book(Map<Enchantment, Integer> enchants) {
        return sum(enchants) * booksMultiplier;
    }

    public double item(Map<Enchantment, Integer> enchants) {
        return sum(enchants) * itemsMultiplier;
    }

    public double booksMultiplier() {
        return booksMultiplier;
    }

    private double sum(Map<Enchantment, Integer> enchants) {
        if (!enabled || enchants == null || enchants.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            total += value(e.getKey(), e.getValue());
        }
        return total;
    }
}
