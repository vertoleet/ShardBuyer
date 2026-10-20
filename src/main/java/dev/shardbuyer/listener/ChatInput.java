package dev.shardbuyer.listener;

import dev.shardbuyer.ShardBuyer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Ввод текста в чат (поиск в /worth). Ответ null — отмена. */
@SuppressWarnings("deprecation")
public final class ChatInput implements Listener {

    private static final class Pending {
        final Consumer<String> callback;
        final long expires;

        Pending(Consumer<String> callback, long expires) {
            this.callback = callback;
            this.expires = expires;
        }
    }

    private final ShardBuyer plugin;
    private static final java.util.Set<String> CANCEL_WORDS = java.util.Set.of(
            "cancel", "отмена", "скасувати", "cancelar", "annuler", "-");

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    public void await(Player p, Consumer<String> callback) {
        pending.put(p.getUniqueId(), new Pending(callback, System.currentTimeMillis() + 60_000L));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Pending p = pending.remove(e.getPlayer().getUniqueId());
        if (p == null) {
            return;
        }
        e.setCancelled(true);
        if (p.expires < System.currentTimeMillis()) {
            return;
        }
        String msg = e.getMessage();
        String lower = msg.trim().toLowerCase(Locale.ROOT);
        String result = CANCEL_WORDS.contains(lower) ? null : msg;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (e.getPlayer().isOnline()) {
                p.callback.accept(result);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }
}
