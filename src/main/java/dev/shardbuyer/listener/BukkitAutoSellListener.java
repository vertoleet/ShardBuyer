package dev.shardbuyer.listener;

import dev.shardbuyer.ShardBuyer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

/** Запасной вариант автопродажи (если вдруг нет Paper-события). */
public final class BukkitAutoSellListener implements Listener {

    private final ShardBuyer plugin;

    public BukkitAutoSellListener(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        if (AutoSellHandler.handle(plugin, p, e.getItem())) {
            e.setCancelled(true);
        }
    }
}
