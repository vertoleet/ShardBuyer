package dev.shardbuyer.exclusive;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.config.Settings;
import dev.shardbuyer.util.TimeFormat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Эксклюзивные предметы: сдаются ограниченное число раз, лимиты (и набор предметов при ротации)
 * обновляются каждые reset-interval.
 */
public final class ExclusiveManager {

    private static final String DATA = "data/exclusive.yml";

    public enum LimitMode { PLAYER, GLOBAL }

    public static final class Item {
        public final String id;
        public final Material material;
        public final double price;
        public final double points;
        public final int limit;
        public final double weight;

        Item(String id, Material material, double price, double points, int limit, double weight) {
            this.id = id;
            this.material = material;
            this.price = price;
            this.points = points;
            this.limit = limit;
            this.weight = weight;
        }
    }

    private final ShardBuyer plugin;
    private final Map<String, Item> pool = new LinkedHashMap<>();
    private boolean enabled;
    private long interval;
    private LimitMode limitMode = LimitMode.PLAYER;
    private boolean rotation;
    private int rotationAmount;
    private boolean applyLevelBoost;
    private boolean givePoints;
    private boolean announce;

    private volatile List<String> current = new ArrayList<>();
    private volatile long nextReset;
    private final Map<UUID, Map<String, Integer>> used = new ConcurrentHashMap<>();
    private final Map<String, Integer> globalUsed = new ConcurrentHashMap<>();

    public ExclusiveManager(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    public void loadSettings() {
        YamlConfiguration cfg = plugin.files().load("exclusive.yml", false);
        enabled = cfg.getBoolean("enabled", true);
        interval = Math.max(60_000L, TimeFormat.parse(cfg.getString("reset-interval", "12h"), 43_200_000L));
        limitMode = Settings.enumValue(LimitMode.class, cfg.getString("limit-mode"), LimitMode.PLAYER, plugin.getLogger(), "exclusive.limit-mode");
        rotation = cfg.getBoolean("rotation.enabled", true);
        rotationAmount = Math.max(1, cfg.getInt("rotation.amount", 5));
        applyLevelBoost = cfg.getBoolean("apply-level-boost", true);
        givePoints = cfg.getBoolean("give-points", true);
        announce = cfg.getBoolean("announce-reset", true);
        pool.clear();
        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Material m = Material.matchMaterial(s.getString("material", id).toUpperCase(java.util.Locale.ROOT));
                if (m == null || !m.isItem()) {
                    plugin.getLogger().warning("exclusive.yml: unknown material for " + id);
                    continue;
                }
                pool.put(id, new Item(id, m, s.getDouble("price", 0), s.getDouble("points", 0),
                        Math.max(1, s.getInt("limit", 16)), Math.max(0.0001, s.getDouble("weight", 1))));
            }
        }
        // убираем из ротации удалённые из конфига предметы
        List<String> cur = new ArrayList<>(current);
        cur.removeIf(id -> !pool.containsKey(id));
        current = cur;
    }

    public boolean enabled() {
        return enabled && !pool.isEmpty();
    }

    public LimitMode limitMode() {
        return limitMode;
    }

    public boolean applyLevelBoost() {
        return applyLevelBoost;
    }

    public boolean givePoints() {
        return givePoints;
    }

    public List<Item> currentItems() {
        List<Item> out = new ArrayList<>();
        for (String id : current) {
            Item it = pool.get(id);
            if (it != null) {
                out.add(it);
            }
        }
        return out;
    }

    public Item get(String id) {
        return pool.get(id);
    }

    public long timeLeft() {
        return Math.max(0, nextReset - System.currentTimeMillis());
    }

    public int used(UUID player, Item item) {
        if (limitMode == LimitMode.GLOBAL) {
            return globalUsed.getOrDefault(item.id, 0);
        }
        Map<String, Integer> m = used.get(player);
        return m == null ? 0 : m.getOrDefault(item.id, 0);
    }

    public int remaining(UUID player, Item item) {
        return Math.max(0, item.limit - used(player, item));
    }

    public void addUsed(UUID player, Item item, int amount) {
        if (limitMode == LimitMode.GLOBAL) {
            globalUsed.merge(item.id, amount, Integer::sum);
        } else {
            used.computeIfAbsent(player, u -> new ConcurrentHashMap<>()).merge(item.id, amount, Integer::sum);
        }
        saveAsync();
    }

    public void tick(long now) {
        if (!enabled()) {
            return;
        }
        if (nextReset <= now || current.isEmpty()) {
            reset(nextReset > 0 && !current.isEmpty());
        }
    }

    /** Сброс лимитов и (если включена ротация) новый набор предметов. */
    public void reset(boolean announceIt) {
        used.clear();
        globalUsed.clear();
        List<String> ids = new ArrayList<>(pool.keySet());
        if (rotation && ids.size() > rotationAmount) {
            current = weightedPick(rotationAmount);
        } else {
            current = ids;
        }
        long now = System.currentTimeMillis();
        if (nextReset <= 0) {
            nextReset = now + interval;
        } else {
            while (nextReset <= now) {
                nextReset += interval;
            }
        }
        saveAsync();
        if (announceIt && announce) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                plugin.messages().send(p, "exclusive.reset-broadcast");
            }
        }
    }

    public void forceReset() {
        nextReset = System.currentTimeMillis() + interval;
        used.clear();
        globalUsed.clear();
        List<String> ids = new ArrayList<>(pool.keySet());
        current = rotation && ids.size() > rotationAmount ? weightedPick(rotationAmount) : ids;
        saveAsync();
    }

    private List<String> weightedPick(int amount) {
        List<Item> left = new ArrayList<>(pool.values());
        List<String> out = new ArrayList<>();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (out.size() < amount && !left.isEmpty()) {
            double total = 0;
            for (Item it : left) {
                total += it.weight;
            }
            double r = rnd.nextDouble() * total;
            Item chosen = left.get(left.size() - 1);
            for (Item it : left) {
                r -= it.weight;
                if (r <= 0) {
                    chosen = it;
                    break;
                }
            }
            out.add(chosen.id);
            left.remove(chosen);
        }
        return out;
    }

    public void loadState() {
        YamlConfiguration y = plugin.files().loadData(DATA);
        nextReset = y.getLong("next-reset", 0);
        List<String> cur = new ArrayList<>(y.getStringList("current"));
        cur.removeIf(id -> !pool.containsKey(id));
        current = cur;
        used.clear();
        globalUsed.clear();
        ConfigurationSection u = y.getConfigurationSection("used");
        if (u != null) {
            for (String k : u.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(k);
                    ConfigurationSection s = u.getConfigurationSection(k);
                    if (s == null) {
                        continue;
                    }
                    Map<String, Integer> m = new ConcurrentHashMap<>();
                    for (String item : s.getKeys(false)) {
                        m.put(item, s.getInt(item));
                    }
                    used.put(id, m);
                } catch (IllegalArgumentException ignored) {
                    // пропуск
                }
            }
        }
        ConfigurationSection g = y.getConfigurationSection("global");
        if (g != null) {
            for (String k : g.getKeys(false)) {
                globalUsed.put(k, g.getInt(k));
            }
        }
    }

    private YamlConfiguration serialize() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-reset", nextReset);
        y.set("current", new ArrayList<>(current));
        Map<UUID, Map<String, Integer>> copy = new HashMap<>(used);
        for (Map.Entry<UUID, Map<String, Integer>> e : copy.entrySet()) {
            for (Map.Entry<String, Integer> i : e.getValue().entrySet()) {
                y.set("used." + e.getKey() + "." + i.getKey(), i.getValue());
            }
        }
        for (Map.Entry<String, Integer> e : globalUsed.entrySet()) {
            y.set("global." + e.getKey(), e.getValue());
        }
        return y;
    }

    public void saveAsync() {
        plugin.files().saveAsync(serialize(), DATA);
    }

    public void saveSync() {
        plugin.files().saveSync(serialize(), DATA);
    }

    public List<Item> pool() {
        return Collections.unmodifiableList(new ArrayList<>(pool.values()));
    }
}
