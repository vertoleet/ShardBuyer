package dev.shardbuyer.gui;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.category.Category;
import dev.shardbuyer.config.Settings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Отслеживает открытые меню и раздаёт им события. */
public final class MenuManager implements Listener {

    /** Меню, которые обновляются каждую секунду (таймеры). */
    public interface Ticking {
        void tick();
    }

    private final ShardBuyer plugin;
    private final Map<UUID, Menu> open = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public MenuManager(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    public void opened(Player p, Menu m) {
        open.put(p.getUniqueId(), m);
    }

    public Menu current(Player p) {
        return open.get(p.getUniqueId());
    }

    public boolean checkCooldown(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < plugin.settings().clickCooldownMs) {
            return false;
        }
        lastClick.put(p.getUniqueId(), now);
        return true;
    }

    // ------------------------------------------------------------------ открытие

    public void openMain(Player p) {
        if (plugin.settings().menuMode == Settings.MenuMode.PAGED) {
            new PagedMenu(plugin, p, null, null).open();
        } else {
            new MainMenu(plugin, p).open();
        }
    }

    public void openCategory(Player p, String id, Menu parent) {
        Category c = plugin.categories().get(id);
        if (c == null || c.items().isEmpty()) {
            plugin.messages().send(p, "unknown-category", "category", id);
            return;
        }
        if (c.permission() != null && !p.hasPermission(c.permission())) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        if (plugin.settings().menuMode == Settings.MenuMode.PAGED) {
            if (parent instanceof PagedMenu pm) {
                pm.jumpTo(c.id());
            } else {
                new PagedMenu(plugin, p, parent, c.id()).open();
            }
        } else {
            Menu back = parent;
            if (back == null || back instanceof CategoryMenu) {
                back = new MainMenu(plugin, p);
            }
            new CategoryMenu(plugin, p, back, c).open();
        }
    }

    public void openSell(Player p) {
        if (!p.hasPermission("shardbuyer.sell")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        new SellMenu(plugin, p).open();
    }

    public void openExclusive(Player p, Menu parent) {
        if (!plugin.exclusive().enabled()) {
            plugin.messages().send(p, "exclusive.disabled");
            return;
        }
        new ExclusiveMenu(plugin, p, parent).open();
    }

    // ------------------------------------------------------------------ события

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof Menu menu) {
            menu.handleClick(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof Menu menu) {
            menu.handleDrag(e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (!(h instanceof Menu menu)) {
            return;
        }
        if (menu.getInventory() != e.getInventory()) {
            return; // меню было переоткрыто с новым заголовком
        }
        UUID id = e.getPlayer().getUniqueId();
        open.remove(id, menu);
        menu.handleClose(e);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastClick.remove(e.getPlayer().getUniqueId());
    }

    public void tick() {
        for (Menu m : new ArrayList<>(open.values())) {
            if (m instanceof Ticking t && m.isViewing()) {
                try {
                    t.tick();
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Menu update error: " + ex);
                }
            }
        }
    }

    /** Закрыть все меню (перезагрузка). */
    public void closeAll() {
        List<Menu> menus = new ArrayList<>(open.values());
        for (Menu m : menus) {
            if (m.player().isOnline()) {
                m.player().closeInventory();
            }
        }
        open.clear();
    }

    /** Выключение плагина: меню продажи возвращают предметы, а не продают. */
    public void shutdown() {
        List<Menu> menus = new ArrayList<>(open.values());
        open.clear();
        for (Menu m : menus) {
            m.shutdown();
            if (m.player().isOnline()) {
                m.player().closeInventory();
            }
        }
    }
}
