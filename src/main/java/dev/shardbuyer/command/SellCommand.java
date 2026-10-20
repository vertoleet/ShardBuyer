package dev.shardbuyer.command;

import dev.shardbuyer.ShardBuyer;
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

/** /sell — меню продажи; /sell hand — предмет в руке; /sell all — весь инвентарь (кроме защищённых предметов). */
public final class SellCommand implements CommandExecutor, TabCompleter {

    private final ShardBuyer plugin;

    public SellCommand(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (!p.hasPermission("shardbuyer.sell")) {
            plugin.messages().send(p, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openSell(p);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "hand" -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    plugin.messages().send(p, "sell.hand-empty");
                    return true;
                }
                if (!plugin.prices().evaluate(hand).sellable && !SellService.isShulkerWithContents(hand)) {
                    plugin.messages().send(p, "sell.hand-unsellable");
                    plugin.settings().soundFail.play(p);
                    return true;
                }
                ItemStack taken = hand.clone();
                p.getInventory().setItemInMainHand(null);
                List<ItemStack> list = new ArrayList<>();
                list.add(taken);
                plugin.sell().sell(p, list, SellService.Source.COMMAND);
            }
            case "all", "inventory", "inv" -> {
                if (!p.hasPermission("shardbuyer.sell.all")) {
                    plugin.messages().send(p, "no-permission");
                    return true;
                }
                plugin.sell().sellInventory(p);
            }
            default -> plugin.messages().send(p, "sell.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String o : List.of("hand", "all")) {
                if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(o);
                }
            }
        }
        return out;
    }
}
