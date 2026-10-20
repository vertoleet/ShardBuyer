package dev.shardbuyer.listener;

import dev.shardbuyer.ShardBuyer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

/** Paper: событие срабатывает даже при полном инвентаре — автопродажа работает и тогда. */
public final class PaperAutoSellListener implements Listener {

    private final ShardBuyer plugin;

    public PaperAutoSellListener(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent e) {
        if (e.getItem().isDead()) {
            return;
        }
        if (AutoSellHandler.handle(plugin, e.getPlayer(), e.getItem())) {
            e.setCancelled(true);
        }
    }
}
