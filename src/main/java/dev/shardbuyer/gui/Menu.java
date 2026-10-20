package dev.shardbuyer.gui;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.util.Placeholders;
import dev.shardbuyer.util.Slots;
import dev.shardbuyer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Базовое меню. Клики обрабатываются со следующего тика (безопасно открывать другие меню). */
public abstract class Menu implements InventoryHolder {

    @FunctionalInterface
    public interface ClickHandler {
        void click(ClickType type);
    }

    protected final ShardBuyer plugin;
    protected final Player player;
    protected final Menu parent;
    protected Inventory inventory;
    protected final Map<Integer, ClickHandler> buttons = new HashMap<>();
    private String currentTitle;

    protected Menu(ShardBuyer plugin, Player player, Menu parent) {
        this.plugin = plugin;
        this.player = player;
        this.parent = parent;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player player() {
        return player;
    }

    /** Секция меню в конфиге. */
    protected abstract ConfigurationSection section();

    /** Заполнение инвентаря и кнопок. */
    protected abstract void render();

    /** Плейсхолдеры меню (для заголовка и кнопок). */
    protected Placeholders placeholders() {
        return Ph.player(plugin, player, null);
    }

    protected String title() {
        ConfigurationSection s = section();
        return s == null ? "Menu" : s.getString("title", "Menu");
    }

    protected int size() {
        ConfigurationSection s = section();
        int size = s == null ? 54 : s.getInt("size", 54);
        size = Math.max(9, Math.min(54, size));
        return size - size % 9;
    }

    public void open() {
        String t = placeholders().apply(title());
        if (t.length() > 128) {
            t = t.substring(0, 128);
        }
        currentTitle = t;
        inventory = Bukkit.createInventory(this, size(), t);
        buttons.clear();
        render();
        player.openInventory(inventory);
        plugin.menus().opened(player, this);
    }

    /** Перерисовать. Если изменился заголовок — меню переоткрывается. */
    public void refresh() {
        if (inventory == null) {
            open();
            return;
        }
        String t = placeholders().apply(title());
        if (t.length() > 128) {
            t = t.substring(0, 128);
        }
        if (!t.equals(currentTitle)) {
            open();
            return;
        }
        clearForRefresh();
        buttons.clear();
        render();
    }

    protected void clearForRefresh() {
        inventory.clear();
    }

    public boolean isViewing() {
        return plugin.menus().current(player) == this && player.isOnline();
    }

    /** Вывод декора/кнопок из layout. */
    protected void applyLayout(MenuLayout layout, Placeholders ph) {
        int size = inventory.getSize();
        for (MenuLayout.Entry e : layout.entries) {
            boolean ok = e.condition == null || ph.test(e.condition);
            if (!ok && e.alt == null) {
                continue;
            }
            ItemStack is = ok ? e.item.build(ph) : e.alt.build(ph);
            String action = ok ? e.action : e.altAction;
            for (int slot : e.slots) {
                if (slot < 0 || slot >= size) {
                    continue;
                }
                inventory.setItem(slot, is.clone());
                if (action != null && !action.isBlank()) {
                    buttons.put(slot, click -> {
                        if (e.requireShift && !click.isShiftClick()) {
                            return;
                        }
                        runAction(action, click);
                    });
                } else {
                    buttons.remove(slot);
                }
            }
        }
    }

    protected int[] itemSlots() {
        ConfigurationSection s = section();
        int[] slots = s == null ? new int[0] : Slots.parse(s.get("item-slots"));
        if (slots.length == 0) {
            slots = Slots.parse("10-16,19-25,28-34,37-43");
        }
        return slots;
    }

    /**
     * Действия кнопок: CLOSE, BACK, OPEN_MAIN, OPEN_SELL, OPEN_WORTH, OPEN_EXCLUSIVE, OPEN_CATEGORY:id,
     * TOGGLE_LORE, TOGGLE_AUTOSELL, SELL_INVENTORY, COMMAND:cmd, CONSOLE:cmd, MESSAGE:text, REFRESH,
     * а также специфичные для меню (NEXT_PAGE, PREV_PAGE, SELL_CATEGORY, SORT, FILTER, SEARCH, ...).
     * Несколько действий можно указать через ";".
     */
    protected void runAction(String rawAction, ClickType click) {
        for (String single : rawAction.split(";")) {
            String action = single.trim();
            if (action.isEmpty()) {
                continue;
            }
            String type = action;
            String arg = "";
            int i = action.indexOf(':');
            if (i > 0) {
                type = action.substring(0, i);
                arg = action.substring(i + 1).trim();
            }
            type = type.trim().toUpperCase(Locale.ROOT);
            plugin.settings().soundClick.play(player);
            if (onAction(type, arg, click)) {
                continue;
            }
            switch (type) {
                case "CLOSE" -> player.closeInventory();
                case "BACK" -> back();
                case "OPEN_MAIN" -> plugin.menus().openMain(player);
                case "OPEN_SELL" -> plugin.menus().openSell(player);
                case "OPEN_WORTH" -> new WorthMenu(plugin, player, this).open();
                case "OPEN_EXCLUSIVE" -> plugin.menus().openExclusive(player, this);
                case "OPEN_CATEGORY" -> plugin.menus().openCategory(player, arg, this);
                case "TOGGLE_LORE" -> {
                    plugin.toggleLore(player);
                    refresh();
                }
                case "TOGGLE_AUTOSELL" -> {
                    plugin.toggleAutosellMaster(player);
                    refresh();
                }
                case "SELL_INVENTORY" -> {
                    if (!player.hasPermission("shardbuyer.sell.all")) {
                        plugin.messages().send(player, "no-permission");
                        continue;
                    }
                    plugin.sell().sellInventory(player);
                    refresh();
                }
                case "REFRESH" -> refresh();
                case "COMMAND" -> player.performCommand(arg.replace("{player}", player.getName()));
                case "CONSOLE" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), arg.replace("{player}", player.getName()));
                case "MESSAGE" -> player.sendMessage(Text.color(arg.replace("{player}", player.getName())));
                default -> plugin.getLogger().warning("Unknown button action: " + action);
            }
        }
    }

    /** Для переопределения: true — действие обработано. */
    protected boolean onAction(String type, String arg, ClickType click) {
        return false;
    }

    protected void back() {
        if (parent != null) {
            parent.refreshOrOpen();
        } else {
            player.closeInventory();
        }
    }

    public void refreshOrOpen() {
        if (isViewing()) {
            refresh();
        } else {
            open();
        }
    }

    // ------------------------------------------------------------------ события

    public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        int size = inventory.getSize();
        if (raw >= size) {
            if (e.isShiftClick() || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
            }
            return;
        }
        e.setCancelled(true);
        if (raw < 0) {
            return;
        }
        ClickHandler h = buttons.get(raw);
        if (h == null) {
            return;
        }
        if (!plugin.menus().checkCooldown(player)) {
            return;
        }
        ClickType type = e.getClick();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isViewing()) {
                h.click(type);
            }
        });
    }

    public void handleDrag(InventoryDragEvent e) {
        int size = inventory.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < size) {
                e.setCancelled(true);
                return;
            }
        }
    }

    public void handleClose(InventoryCloseEvent e) {
        // по умолчанию ничего
    }

    /** Вызывается при выключении плагина. */
    public void shutdown() {
        // по умолчанию ничего
    }
}
