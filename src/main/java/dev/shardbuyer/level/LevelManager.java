package dev.shardbuyer.level;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.category.Category;
import dev.shardbuyer.config.Settings;
import dev.shardbuyer.data.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Прокачка: GLOBAL — общий уровень, PER_CATEGORY — свой уровень в каждой категории, DISABLED — выключено.
 * У каждой категории можно отключить прокачку (leveling: false в categories.yml).
 */
public final class LevelManager {

    public enum Mode { GLOBAL, PER_CATEGORY, DISABLED }

    public static final String GLOBAL_KEY = "global";

    public static final class Info {
        public final int level;
        public final int maxLevel;
        public final double points;
        public final double bonus;
        public final double nextPoints;
        public final double nextBonus;
        public final boolean maxed;
        public final double progress;

        Info(int level, int maxLevel, double points, double bonus, double nextPoints, double nextBonus,
             boolean maxed, double progress) {
            this.level = level;
            this.maxLevel = maxLevel;
            this.points = points;
            this.bonus = bonus;
            this.nextPoints = nextPoints;
            this.nextBonus = nextBonus;
            this.maxed = maxed;
            this.progress = progress;
        }
    }

    private final ShardBuyer plugin;
    private Mode mode = Mode.GLOBAL;
    private double pointsPerMoney = 1.0;
    private LevelTable global;
    private final Map<String, LevelTable> perCategory = new HashMap<>();

    public LevelManager(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    public void load(YamlConfiguration cfg) {
        mode = Settings.enumValue(Mode.class, cfg.getString("leveling.mode"), Mode.GLOBAL, plugin.getLogger(), "leveling.mode");
        pointsPerMoney = cfg.getDouble("leveling.points-per-money", 1.0);
        ConfigurationSection s = cfg.getConfigurationSection("leveling");
        global = LevelTable.from(s);
        perCategory.clear();
        for (Category c : plugin.categories().all()) {
            if (c.levels() != null) {
                perCategory.put(c.id(), LevelTable.from(c.levels()));
            }
        }
    }

    public Mode mode() {
        return mode;
    }

    public double pointsPerMoney() {
        return pointsPerMoney;
    }

    /** Активна ли прокачка для категории (null — предмет вне категорий). */
    public boolean isActive(String categoryId) {
        if (mode == Mode.DISABLED) {
            return false;
        }
        if (categoryId == null) {
            return mode == Mode.GLOBAL;
        }
        Category c = plugin.categories().get(categoryId);
        return c == null ? mode == Mode.GLOBAL : c.leveling();
    }

    /** Ключ, по которому хранятся очки игрока, или null если прокачка неактивна. */
    public String key(String categoryId) {
        if (!isActive(categoryId)) {
            return null;
        }
        return mode == Mode.GLOBAL ? GLOBAL_KEY : (categoryId == null ? GLOBAL_KEY : categoryId);
    }

    public LevelTable table(String key) {
        if (key == null || GLOBAL_KEY.equals(key)) {
            return global;
        }
        return perCategory.getOrDefault(key, global);
    }

    public Info info(PlayerData data, String categoryId) {
        String key = key(categoryId);
        if (key == null) {
            return null;
        }
        return infoByKey(data, key);
    }

    public Info infoByKey(PlayerData data, String key) {
        LevelTable t = table(key);
        double pts = data == null ? 0 : data.points(key);
        LevelTable.Level cur = t.levelFor(pts);
        LevelTable.Level next = t.next(cur);
        double progress = next == null ? 1.0
                : Math.max(0, Math.min(1, (pts - cur.points) / Math.max(1e-9, next.points - cur.points)));
        return new Info(cur.level, t.max().level, pts, cur.bonus,
                next == null ? cur.points : next.points, next == null ? cur.bonus : next.bonus,
                next == null, progress);
    }

    /** Множитель цены от уровня (1.0 = без бонуса). */
    public double multiplier(PlayerData data, String categoryId) {
        Info i = info(data, categoryId);
        return i == null ? 1.0 : 1.0 + i.bonus / 100.0;
    }
}
