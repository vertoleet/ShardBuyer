package dev.shardbuyer.hook;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.data.PlayerData;
import dev.shardbuyer.level.LevelManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Плейсхолдеры PlaceholderAPI:
 * %shardbuyer_level%, %shardbuyer_level_&lt;категория&gt;%, %shardbuyer_points%, %shardbuyer_points_&lt;категория&gt;%,
 * %shardbuyer_next_points%, %shardbuyer_progress%, %shardbuyer_bonus%, %shardbuyer_boost%, %shardbuyer_earned%, %shardbuyer_sold%,
 * %shardbuyer_booster%, %shardbuyer_booster_time%, %shardbuyer_exclusive_time%, %shardbuyer_autosell%, %shardbuyer_lore%
 */
public final class PapiExpansion extends PlaceholderExpansion {

    private final ShardBuyer plugin;

    public PapiExpansion(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "shardbuyer";
    }

    @Override
    public String getAuthor() {
        return "ShardBuyer";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer op, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        switch (p) {
            case "booster":
                return plugin.formatMultiplier(plugin.booster().multiplier());
            case "booster_time":
                return plugin.timeFormat().format(plugin.booster().timeLeft());
            case "exclusive_time":
                return plugin.timeFormat().format(plugin.exclusive().timeLeft());
            default:
                break;
        }
        if (op == null) {
            return "";
        }
        PlayerData d = plugin.data().peek(op.getUniqueId());
        if (d == null) {
            return "";
        }
        String category = null;
        String key = p;
        int us = p.indexOf('_');
        if (us > 0) {
            String head = p.substring(0, us);
            String tail = p.substring(us + 1);
            if (plugin.categories().get(tail) != null && (head.equals("level") || head.equals("points")
                    || head.equals("progress") || head.equals("bonus") || head.equals("next"))) {
                key = head;
                category = tail;
            }
        }
        LevelManager.Info info = plugin.levels().info(d, category);
        switch (key) {
            case "level":
                return info == null ? "-" : String.valueOf(info.level);
            case "points":
                return info == null ? "-" : plugin.pointsFormat().format(info.points);
            case "next_points":
            case "next":
                return info == null ? "-" : plugin.pointsFormat().format(info.nextPoints);
            case "progress":
                return info == null ? "-" : plugin.formatPercent(info.progress * 100);
            case "bonus":
                return info == null ? "0" : plugin.formatPercent(info.bonus);
            case "boost":
                return plugin.formatPercent((plugin.sell().multiplier(d, category) - 1) * 100);
            case "earned":
                return plugin.moneyFormat().format(d.earned());
            case "sold":
                return String.valueOf(d.sold());
            case "autosell":
                return plugin.messages().get(d.autosellEnabled() ? "status.enabled" : "status.disabled");
            case "lore":
                Player online = op.getPlayer();
                return plugin.messages().get(online != null && plugin.loreEnabled(online) ? "status.enabled" : "status.disabled");
            default:
                return null;
        }
    }
}
