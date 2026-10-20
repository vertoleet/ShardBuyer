package dev.shardbuyer.command;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.category.Category;
import dev.shardbuyer.gui.WorthMenu;
import dev.shardbuyer.price.ItemValue;
import dev.shardbuyer.sell.SellService;
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
 * /worth — меню цен; /worth hand — цена предмета в руке; /worth lore — вкл/выкл цену в описании;
 * /worth &lt;категория&gt; — меню с фильтром; /worth &lt;текст&gt; — меню с поиском.
 */
public final class WorthCommand implements CommandExecutor, TabCompleter {

    private final ShardBuyer plugin;

    public WorthCommand(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (!p.hasPermission("shardbuyer.worth")) {
            plugin.messages().send(p, "no-permission");
            return true;
        }
        if (args.length == 0) {
            new WorthMenu(plugin, p, null).open();
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "hand" -> hand(p);
            case "lore" -> plugin.toggleLore(p);
            default -> {
                Category c = plugin.categories().get(sub);
                if (c != null) {
                    new WorthMenu(plugin, p, null).filter(c.id()).open();
                } else {
                    new WorthMenu(plugin, p, null).search(String.join(" ", args)).open();
                }
            }
        }
        return true;
    }

    private void hand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            plugin.messages().send(p, "sell.hand-empty");
            return;
        }
        double total = plugin.sell().displayValue(p.getUniqueId(), hand);
        if (total <= 0) {
            ItemValue v = plugin.prices().evaluate(hand);
            plugin.messages().send(p, "worth.unsellable", "item", plugin.names().itemArg(hand.getType()),
                    "reason", plugin.messages().get("reasons." + v.reason.name().toLowerCase(Locale.ROOT)));
            return;
        }
        plugin.messages().send(p, SellService.isShulkerWithContents(hand) ? "worth.hand-shulker" : "worth.hand",
                "item", plugin.names().itemArg(hand.getType()),
                "amount", hand.getAmount(),
                "price", plugin.moneyFormat().format(total),
                "price_one", plugin.moneyFormat().format(total / hand.getAmount()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("hand", "lore"));
            for (Category c : plugin.categories().all()) {
                opts.add(c.id());
            }
            for (String o : opts) {
                if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(o);
                }
            }
        }
        return out;
    }
}
