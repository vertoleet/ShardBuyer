package dev.shardbuyer.command;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.category.Category;
import dev.shardbuyer.data.PlayerData;
import dev.shardbuyer.level.LevelManager;
import dev.shardbuyer.price.ItemValue;
import dev.shardbuyer.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /shardbuyer reload
 * /shardbuyer setprice &lt;цена&gt; [предмет]   — без предмета берётся предмет в руке
 * /shardbuyer delprice [предмет]
 * /shardbuyer info                       — разбор цены предмета в руке
 * /shardbuyer points &lt;игрок&gt; &lt;add|set|take&gt; &lt;кол-во&gt; [категория]
 * /shardbuyer resetlevel &lt;игрок&gt;
 * /shardbuyer market &lt;reset|info&gt; [предмет]
 * /shardbuyer exclusive reset
 * /shardbuyer booster &lt;множитель&gt; &lt;время&gt; | booster stop
 * /shardbuyer dump                       — выгрузить все цены в computed-prices.yml
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final ShardBuyer plugin;

    public AdminCommand(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardbuyer.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(sender, "admin.help");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                long start = System.currentTimeMillis();
                plugin.reload();
                plugin.messages().send(sender, "reloaded", "ms", System.currentTimeMillis() - start,
                        "count", plugin.prices().all().size());
            }
            case "setprice" -> {
                if (args.length < 2) {
                    plugin.messages().send(sender, "admin.help");
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[1].replace(',', '.'));
                } catch (NumberFormatException e) {
                    plugin.messages().send(sender, "admin.bad-number", "value", args[1]);
                    return true;
                }
                Material m = material(sender, args, 2);
                if (m == null) {
                    return true;
                }
                plugin.prices().setPrice(m, price);
                plugin.reloadPrices();
                plugin.messages().send(sender, "admin.price-set", "item", plugin.names().itemArg(m),
                        "price", plugin.moneyFormat().format(price));
            }
            case "delprice" -> {
                Material m = material(sender, args, 1);
                if (m == null) {
                    return true;
                }
                plugin.prices().removePrice(m);
                plugin.reloadPrices();
                plugin.messages().send(sender, "admin.price-removed", "item", plugin.names().itemArg(m),
                        "price", plugin.moneyFormat().format(plugin.prices().base(m)));
            }
            case "info" -> info(sender);
            case "points" -> points(sender, args);
            case "resetlevel" -> {
                if (args.length < 2) {
                    plugin.messages().send(sender, "admin.help");
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                PlayerData d = plugin.data().getOffline(op);
                d.clearPoints();
                plugin.data().save(d, true);
                plugin.messages().send(sender, "admin.level-reset", "player", args[1]);
            }
            case "market" -> market(sender, args);
            case "exclusive" -> {
                plugin.exclusive().forceReset();
                plugin.messages().send(sender, "admin.exclusive-reset");
            }
            case "booster" -> booster(sender, args);
            case "dump" -> {
                int n = plugin.prices().dump();
                plugin.messages().send(sender, "admin.dumped", "count", n);
            }
            default -> plugin.messages().send(sender, "admin.help");
        }
        return true;
    }

    private Material material(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Material m = Material.matchMaterial(args[index].toUpperCase(Locale.ROOT));
            if (m == null || !m.isItem() || m.isAir()) {
                plugin.messages().send(sender, "admin.unknown-item", "item", args[index]);
                return null;
            }
            return m;
        }
        if (sender instanceof Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand != null && !hand.getType().isAir()) {
                return hand.getType();
            }
        }
        plugin.messages().send(sender, "sell.hand-empty");
        return null;
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            plugin.messages().send(p, "sell.hand-empty");
            return;
        }
        ItemValue v = plugin.prices().evaluate(hand);
        Material m = hand.getType();
        plugin.messages().send(p, "admin.info",
                "item", plugin.names().itemArg(m),
                "material", m.name(),
                "sellable", plugin.messages().get(v.sellable ? "status.enabled" : "status.disabled"),
                "reason", plugin.messages().get("reasons." + v.reason.name().toLowerCase(Locale.ROOT)),
                "base", plugin.moneyFormat().format(plugin.prices().base(m)),
                "source", plugin.messages().get(plugin.prices().isManual(m) ? "admin.source-manual" : "admin.source-auto"),
                "unit", plugin.moneyFormat().format(v.unitBase),
                "player_price", plugin.moneyFormat().format(plugin.sell().unitPrice(p.getUniqueId(), v)),
                "market", plugin.formatPercent(plugin.market().factor(v.marketKey == null ? m.name() : v.marketKey) * 100),
                "market_key", v.marketKey == null ? "-" : v.marketKey,
                "sold", plugin.pointsFormat().format(plugin.market().sold(v.marketKey == null ? m.name() : v.marketKey)),
                "category", v.category == null ? "-" : plugin.categories().displayName(v.category),
                "points", plugin.pointsFormat().format(v.unitPoints));
    }

    @SuppressWarnings("deprecation")
    private void points(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.messages().send(sender, "admin.help");
            return;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
        String action = args[2].toLowerCase(Locale.ROOT);
        double amount;
        try {
            amount = Double.parseDouble(args[3].replace(',', '.'));
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "admin.bad-number", "value", args[3]);
            return;
        }
        LevelManager lm = plugin.levels();
        String key;
        if (lm.mode() == LevelManager.Mode.PER_CATEGORY) {
            if (args.length < 5 || plugin.categories().get(args[4]) == null) {
                plugin.messages().send(sender, "admin.need-category");
                return;
            }
            key = plugin.categories().get(args[4]).id();
        } else {
            key = LevelManager.GLOBAL_KEY;
        }
        PlayerData d = plugin.data().getOffline(op);
        switch (action) {
            case "add", "give" -> d.addPoints(key, amount);
            case "take", "remove" -> d.addPoints(key, -amount);
            case "set" -> d.setPoints(key, amount);
            default -> {
                plugin.messages().send(sender, "admin.help");
                return;
            }
        }
        plugin.data().save(d, true);
        plugin.messages().send(sender, "admin.points-changed", "player", args[1],
                "points", plugin.pointsFormat().format(d.points(key)),
                "level", lm.infoByKey(d, key).level);
    }

    private void market(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "admin.help");
            return;
        }
        String key = args.length > 2 ? args[2].toUpperCase(Locale.ROOT) : null;
        if (args[1].equalsIgnoreCase("reset")) {
            plugin.market().reset(key == null || key.equals("ALL") ? null : key);
            plugin.messages().send(sender, "admin.market-reset", "item", key == null ? "ALL" : key);
        } else if (args[1].equalsIgnoreCase("info")) {
            if (key == null) {
                plugin.messages().send(sender, "admin.help");
                return;
            }
            plugin.messages().send(sender, "admin.market-info", "item", key,
                    "sold", plugin.pointsFormat().format(plugin.market().sold(key)),
                    "percent", plugin.formatPercent(plugin.market().factor(key) * 100));
        }
    }

    private void booster(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            plugin.booster().stop();
            plugin.messages().send(sender, "booster.stopped");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "admin.help");
            return;
        }
        double mult;
        try {
            mult = Double.parseDouble(args[1].replace(',', '.').replace("x", ""));
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "admin.bad-number", "value", args[1]);
            return;
        }
        long time = TimeFormat.parse(args[2], -1);
        if (time <= 0 || mult <= 0) {
            plugin.messages().send(sender, "admin.bad-number", "value", args[2]);
            return;
        }
        plugin.booster().start(mult, time);
        String msg = plugin.messages().get("booster.started", "multiplier", plugin.formatMultiplier(mult),
                "time", plugin.timeFormat().format(time));
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.messages().sendRaw(p, msg);
        }
        plugin.messages().sendRaw(Bukkit.getConsoleSender(), msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opts = new ArrayList<>();
        if (args.length == 1) {
            opts.addAll(List.of("reload", "setprice", "delprice", "info", "points", "resetlevel", "market",
                    "exclusive", "booster", "dump"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "points", "resetlevel" -> Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
                case "market" -> opts.addAll(List.of("reset", "info"));
                case "exclusive" -> opts.add("reset");
                case "booster" -> opts.addAll(List.of("1.5", "2", "stop"));
                case "delprice" -> opts.addAll(materials(args[1]));
                default -> {
                }
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "points" -> opts.addAll(List.of("add", "set", "take"));
                case "setprice" -> opts.addAll(materials(args[2]));
                case "market" -> opts.addAll(materials(args[2]));
                case "booster" -> opts.addAll(List.of("30m", "1h", "2h", "1d"));
                default -> {
                }
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("points")) {
            for (Category c : plugin.categories().all()) {
                opts.add(c.id());
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                out.add(o);
            }
        }
        return out.size() > 80 ? out.subList(0, 80) : out;
    }

    private List<String> materials(String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toUpperCase(Locale.ROOT);
        if (p.length() < 2) {
            return out;
        }
        for (Material m : plugin.prices().all().keySet()) {
            if (m.name().startsWith(p)) {
                out.add(m.name());
            }
        }
        return out;
    }
}
