package dev.shardbuyer.gui;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.config.Settings;
import dev.shardbuyer.data.PlayerData;
import dev.shardbuyer.level.LevelManager;
import dev.shardbuyer.util.Placeholders;
import dev.shardbuyer.util.Text;
import org.bukkit.entity.Player;

/** Общие плейсхолдеры игрока для меню. */
public final class Ph {

    private Ph() {
    }

    public static Placeholders player(ShardBuyer plugin, Player p, String categoryId) {
        Placeholders ph = new Placeholders();
        PlayerData d = plugin.data().get(p);
        ph.put("player", p.getName());
        plugin.commandPlaceholders().forEach(ph::put);
        ph.put("balance", plugin.moneyFormat().format(plugin.economy().balance(p)));
        level(plugin, ph, plugin.levels().info(d, categoryId));

        double mult = plugin.sell().multiplier(d, categoryId);
        ph.put("boost", plugin.formatPercent((mult - 1) * 100));
        ph.flag("boosted", mult > 1.0001);
        ph.put("perm_boost", plugin.formatPercent((d.permMultiplier() - 1) * 100));
        ph.flag("perm_boost", d.permMultiplier() > 1.0001);

        boolean booster = plugin.booster().active();
        ph.flag("booster", booster);
        ph.put("booster", plugin.formatMultiplier(plugin.booster().multiplier()));
        ph.put("booster_time", plugin.timeFormat().format(plugin.booster().timeLeft()));

        boolean loreAvailable = plugin.loreAvailable();
        boolean loreOn = plugin.loreEnabled(p);
        ph.flag("lore_available", loreAvailable);
        ph.flag("lore_on", loreOn);
        ph.put("lore_status", status(plugin, loreOn));

        Settings s = plugin.settings();
        boolean autoAvailable = s.autosellEnabled && p.hasPermission("shardbuyer.autosell");
        ph.flag("autosell_available", autoAvailable);
        ph.flag("autosell_on", d.autosellEnabled());
        ph.put("autosell_status", status(plugin, d.autosellEnabled()));
        ph.put("autosell_count", d.autosell().size());

        ph.flag("exclusive_available", plugin.exclusive().enabled());
        ph.put("exclusive_time", plugin.timeFormat().format(plugin.exclusive().timeLeft()));

        ph.flag("market", plugin.market().enabled());
        ph.put("market_recovery", plugin.timeFormat().format(plugin.market().msToRecovery()));

        ph.put("earned", plugin.moneyFormat().format(d.earned()));
        ph.put("sold", plugin.pointsFormat().format(d.sold()));
        ph.put("level_mode", plugin.messages().get("level.mode." + plugin.levels().mode().name().toLowerCase(java.util.Locale.ROOT)));
        return ph;
    }

    public static void level(ShardBuyer plugin, Placeholders ph, LevelManager.Info info) {
        ph.flag("leveling", info != null);
        if (info == null) {
            for (String k : new String[]{"level", "max_level", "points", "next_points", "points_left", "progress",
                    "progress_bar", "bonus", "next_bonus", "next_level"}) {
                ph.put(k, "-");
            }
            ph.flag("maxed", false);
            return;
        }
        ph.put("level", info.level);
        ph.put("max_level", info.maxLevel);
        ph.put("next_level", info.maxed ? info.level : info.level + 1);
        ph.put("points", plugin.pointsFormat().format(info.points));
        ph.put("next_points", plugin.pointsFormat().format(info.nextPoints));
        ph.put("points_left", plugin.pointsFormat().format(Math.max(0, info.nextPoints - info.points)));
        ph.put("progress", plugin.formatPercent(info.progress * 100));
        ph.put("progress_bar", bar(plugin, info.progress));
        ph.put("bonus", plugin.formatPercent(info.bonus));
        ph.put("next_bonus", plugin.formatPercent(info.nextBonus));
        ph.flag("maxed", info.maxed);
    }

    public static String bar(ShardBuyer plugin, double progress) {
        Settings s = plugin.settings();
        int len = s.barLength;
        int done = (int) Math.round(Math.max(0, Math.min(1, progress)) * len);
        StringBuilder sb = new StringBuilder(s.barComplete);
        for (int i = 0; i < done; i++) {
            sb.append(s.barSymbol);
        }
        sb.append(s.barIncomplete);
        for (int i = done; i < len; i++) {
            sb.append(s.barSymbol);
        }
        return Text.color(sb.toString());
    }

    public static String status(ShardBuyer plugin, boolean on) {
        return plugin.messages().get(on ? "status.enabled" : "status.disabled");
    }
}
