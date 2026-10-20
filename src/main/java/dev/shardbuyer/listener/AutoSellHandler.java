package dev.shardbuyer.listener;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.data.PlayerData;
import dev.shardbuyer.sell.SellService;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Общая логика автопродажи при подборе предмета. */
final class AutoSellHandler {

    private AutoSellHandler() {
    }

    /** @return true если предмет продан и подбор нужно отменить */
    static boolean handle(ShardBuyer plugin, Player p, Item entity) {
        if (!plugin.settings().autosellEnabled || !p.hasPermission("shardbuyer.autosell")) {
            return false;
        }
        PlayerData d = plugin.data().peek(p.getUniqueId());
        if (d == null || !d.autosellEnabled() || d.autosell().isEmpty()) {
            return false;
        }
        ItemStack is = entity.getItemStack();
        if (is == null || !d.autosell().contains(is.getType())) {
            return false;
        }
        if (!plugin.sell().quickSellable(is)) {
            return false;
        }
        SellService.Quote q = plugin.sell().quote(p, List.of(is.clone()));
        if (q.amount <= 0 || !q.leftovers.isEmpty()) {
            return false;
        }
        if (plugin.sell().sell(p, List.of(is.clone()), SellService.Source.AUTOSELL) == null) {
            return false;
        }
        entity.remove();
        return true;
    }
}
