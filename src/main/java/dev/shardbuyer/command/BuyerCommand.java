package dev.shardbuyer.command;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.category.Category;
import dev.shardbuyer.data.PlayerData;
import dev.shardbuyer.level.LevelManager;
import dev.shardbuyer.gui.WorthMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /buyer [категория | lore | autosell | level | sell | worth | exclusive | help] */
public final class BuyerCommand implements CommandExecutor, TabCompleter {

    private final ShardBuyer plugin;

    public BuyerCommand(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (!p.hasPermission("shardbuyer.use")) {
            plugin.messages().send(p, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openMain(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "lore", "price" -> plugin.toggleLore(p);
            case "autosell", "auto" -> plugin.toggleAutosellMaster(p);
            case "level", "lvl" -> level(p);
            case "sell" -> plugin.menus().openSell(p);
            case "worth", "prices" -> new WorthMenu(plugin, p, null).open();
            case "exclusive", "ex" -> plugin.menus().openExclusive(p, null);
            case "help" -> plugin.messages().send(p, "help");
            default -> plugin.menus().openCategory(p, sub, null);
        }
        return true;
    }

    private void level(Player p) {
        LevelManager lm = plugin.levels();
        if (lm.mode() == LevelManager.Mode.DISABLED) {
            plugin.messages().send(p, "level.disabled");
            return;
        }
        PlayerData d = plugin.data().get(p);
        plugin.messages().send(p, "level.info-header");
        if (lm.mode() == LevelManager.Mode.GLOBAL) {
            sendInfo(p, lm.infoByKey(d, LevelManager.GLOBAL_KEY), "");
        } else {
            for (Category c : plugin.categories().visible(p)) {
                LevelManager.Info info = lm.info(d, c.id());
                if (info != null) {
                    sendInfo(p, info, c.name());
                }
            }
        }
    }

    private void sendInfo(Player p, LevelManager.Info info, String category) {
        plugin.messages().send(p, info.maxed ? "level.info-line-max" : "level.info-line",
                "category", category,
                "level", info.level,
                "max_level", info.maxLevel,
                "points", plugin.pointsFormat().format(info.points),
                "next_points", plugin.pointsFormat().format(info.nextPoints),
                "bonus", plugin.formatPercent(info.bonus),
                "next_bonus", plugin.formatPercent(info.nextBonus),
                "progress", plugin.formatPercent(info.progress * 100),
                "progress_bar", dev.shardbuyer.gui.Ph.bar(plugin, info.progress));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("lore", "autosell", "level", "sell", "worth", "exclusive", "help"));
            if (sender instanceof Player p) {
                for (Category c : plugin.categories().visible(p)) {
                    opts.add(c.id());
                }
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String o : opts) {
                if (o.startsWith(prefix)) {
                    out.add(o);
                }
            }
        }
        return out;
    }
}
