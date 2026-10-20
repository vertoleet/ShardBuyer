package dev.shardbuyer.market;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.util.TimeFormat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Динамические цены: чем больше предмета сдали все игроки — тем ниже цена (и очки).
 * Проданное количество постепенно «забывается» (восстановление цены).
 */
public final class MarketManager {

    private static final String FILE = "data/market.yml";

    private static final class Rule {
        final double unitsPerStep;
        final double stepPercent;
        final double minPercent;

        Rule(double unitsPerStep, double stepPercent, double minPercent) {
            this.unitsPerStep = Math.max(1, unitsPerStep);
            this.stepPercent = Math.max(0, stepPercent);
            this.minPercent = Math.max(0, Math.min(100, minPercent));
        }
    }

    private final ShardBuyer plugin;
    private final Map<String, Double> sold = new ConcurrentHashMap<>();
    private volatile boolean enabled;
    private volatile boolean affectPoints;
    private Rule def = new Rule(2000, 5, 25);
    private final Map<String, Rule> overrides = new HashMap<>();
    private final Set<Material> exclude = EnumSet.noneOf(Material.class);
    private long recoveryInterval;
    private double recoveryPercent;
    private long lastRecovery = System.currentTimeMillis();
    private boolean dirty;

    public MarketManager(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    public void loadSettings(YamlConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("dynamic-pricing");
        enabled = s != null && s.getBoolean("enabled", false);
        if (s == null) {
            return;
        }
        affectPoints = s.getBoolean("affect-points", true);
        def = rule(s, null);
        overrides.clear();
        ConfigurationSection o = s.getConfigurationSection("overrides");
        if (o != null) {
            for (String k : o.getKeys(false)) {
                overrides.put(k.toUpperCase(Locale.ROOT), rule(o.getConfigurationSection(k), def));
            }
        }
        exclude.clear();
        for (String n : s.getStringList("exclude")) {
            Material m = Material.matchMaterial(n.toUpperCase(Locale.ROOT));
            if (m != null) {
                exclude.add(m);
            }
        }
        recoveryInterval = TimeFormat.parse(s.getString("recovery.interval", "10m"), 600_000L);
        recoveryPercent = s.getDouble("recovery.percent", 10);
    }

    private static Rule rule(ConfigurationSection s, Rule parent) {
        if (s == null) {
            return parent;
        }
        return new Rule(
                s.getDouble("units-per-step", parent == null ? 2000 : parent.unitsPerStep),
                s.getDouble("step-percent", parent == null ? 5 : parent.stepPercent),
                s.getDouble("min-percent", parent == null ? 25 : parent.minPercent));
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean affectsPoints() {
        return enabled && affectPoints;
    }

    private Rule ruleFor(String key) {
        String mat = key;
        int i = key.indexOf(':');
        if (i > 0) {
            mat = key.substring(0, i);
        }
        Rule r = overrides.get(key.toUpperCase(Locale.ROOT));
        if (r == null) {
            r = overrides.get(mat);
        }
        return r == null ? def : r;
    }

    private boolean excluded(String key) {
        if (key == null) {
            return true;
        }
        int i = key.indexOf(':');
        Material m = Material.matchMaterial(i > 0 ? key.substring(0, i) : key);
        return m != null && exclude.contains(m);
    }

    private double factorAt(String key, double units) {
        Rule r = ruleFor(key);
        double f = 1.0 - (units / r.unitsPerStep) * (r.stepPercent / 100.0);
        return Math.max(r.minPercent / 100.0, Math.min(1.0, f));
    }

    /** Текущий множитель цены (1.0 = без падения). */
    public double factor(String key) {
        if (!enabled || excluded(key)) {
            return 1.0;
        }
        return factorAt(key, sold.getOrDefault(key, 0.0));
    }

    /** Средний множитель при продаже amount штук (цена падает по ходу продажи). */
    public double factorFor(String key, long amount) {
        if (!enabled || excluded(key)) {
            return 1.0;
        }
        double cur = sold.getOrDefault(key, 0.0);
        return factorAt(key, cur + amount / 2.0);
    }

    public void record(String key, long amount) {
        if (!enabled || excluded(key) || amount <= 0) {
            return;
        }
        sold.merge(key, (double) amount, Double::sum);
        dirty = true;
    }

    public double sold(String key) {
        return sold.getOrDefault(key, 0.0);
    }

    public void reset(String key) {
        if (key == null) {
            sold.clear();
        } else {
            sold.keySet().removeIf(k -> k.equals(key) || k.startsWith(key + ":"));
        }
        dirty = true;
    }

    /** Вызывается раз в секунду. */
    public void tick(long now) {
        if (!enabled || recoveryInterval <= 0) {
            lastRecovery = now;
            return;
        }
        if (now - lastRecovery < recoveryInterval) {
            return;
        }
        lastRecovery = now;
        double keep = 1.0 - recoveryPercent / 100.0;
        sold.replaceAll((k, v) -> v * keep);
        sold.values().removeIf(v -> v < 0.5);
        dirty = true;
    }

    public long msToRecovery() {
        return Math.max(0, recoveryInterval - (System.currentTimeMillis() - lastRecovery));
    }

    public void load() {
        YamlConfiguration y = plugin.files().loadData(FILE);
        sold.clear();
        ConfigurationSection s = y.getConfigurationSection("sold");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                sold.put(k.replace('|', ':'), s.getDouble(k));
            }
        }
        lastRecovery = y.getLong("last-recovery", System.currentTimeMillis());
    }

    public void save(boolean async) {
        if (!dirty && async) {
            return;
        }
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Double> e : sold.entrySet()) {
            // ':' и '.' нельзя в ключах yml-путей
            y.set("sold." + e.getKey().replace(':', '|').replace('.', '_'), Math.round(e.getValue() * 100) / 100.0);
        }
        y.set("last-recovery", lastRecovery);
        if (async) {
            plugin.files().saveAsync(y, FILE);
        } else {
            plugin.files().saveSync(y, FILE);
        }
    }
}
