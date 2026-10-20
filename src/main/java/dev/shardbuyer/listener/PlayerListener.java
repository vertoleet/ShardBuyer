package dev.shardbuyer.listener;

import dev.shardbuyer.ShardBuyer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {

    private final ShardBuyer plugin;

    public PlayerListener(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        plugin.data().get(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        // меню продажи закроется само (InventoryCloseEvent) раньше, чем выгрузятся данные
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getPlayer(e.getPlayer().getUniqueId()) == null) {
                plugin.data().unload(e.getPlayer().getUniqueId());
            }
        });
    }

    /** При смене режима игры пересылаем инвентарь — чтобы цена в описании появилась/исчезла. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        if (plugin.loreHook() == null) {
            return;
        }
        if (e.getNewGameMode() == GameMode.CREATIVE || e.getPlayer().getGameMode() == GameMode.CREATIVE) {
            Bukkit.getScheduler().runTask(plugin, () -> e.getPlayer().updateInventory());
        }
    }
}
