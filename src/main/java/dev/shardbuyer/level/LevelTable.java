package dev.shardbuyer.level;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Таблица уровней: сколько очков нужно для уровня и какой бонус к цене он даёт. */
public final class LevelTable {

    public static final class Level {
        public final int level;
        public final double points;
        public final double bonus;

        Level(int level, double points, double bonus) {
            this.level = level;
            this.points = points;
            this.bonus = bonus;
        }
    }

    private final List<Level> levels;

    private LevelTable(List<Level> levels) {
        this.levels = levels;
    }

    /**
     * @param s секция с type: LIST|FORMULA, levels: {...}, formula: {...}
     */
    public static LevelTable from(ConfigurationSection s) {
        List<Level> list = new ArrayList<>();
        if (s != null) {
            String type = s.getString("type", "LIST").toUpperCase(Locale.ROOT);
            ConfigurationSection lv = s.getConfigurationSection("levels");
            if (type.equals("LIST") && lv != null && !lv.getKeys(false).isEmpty()) {
                for (String k : lv.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(k.trim());
                        ConfigurationSection l = lv.getConfigurationSection(k);
                        if (l == null) {
                            continue;
                        }
                        list.add(new Level(level, l.getDouble("points", 0), l.getDouble("bonus", 0)));
                    } catch (NumberFormatException ignored) {
                        // пропуск
                    }
                }
            } else {
                ConfigurationSection f = s.getConfigurationSection("formula");
                int max = f == null ? 50 : Math.max(1, f.getInt("max-level", 50));
                double base = f == null ? 1000 : f.getDouble("base-points", 1000);
                double growth = f == null ? 1.3 : f.getDouble("growth", 1.3);
                double bonus = f == null ? 1.0 : f.getDouble("bonus-per-level", 1.0);
                double total = 0;
                list.add(new Level(1, 0, 0));
                for (int n = 2; n <= max; n++) {
                    total += base * Math.pow(growth, n - 2);
                    list.add(new Level(n, Math.round(total), (n - 1) * bonus));
                }
            }
        }
        if (list.isEmpty()) {
            list.add(new Level(1, 0, 0));
        }
        list.sort((a, b) -> Integer.compare(a.level, b.level));
        return new LevelTable(list);
    }

    public Level levelFor(double points) {
        Level cur = levels.get(0);
        for (Level l : levels) {
            if (points >= l.points) {
                cur = l;
            } else {
                break;
            }
        }
        return cur;
    }

    /** @return следующий уровень или null, если максимальный */
    public Level next(Level current) {
        int idx = levels.indexOf(current);
        return idx >= 0 && idx + 1 < levels.size() ? levels.get(idx + 1) : null;
    }

    public Level max() {
        return levels.get(levels.size() - 1);
    }

    public List<Level> levels() {
        return levels;
    }
}
