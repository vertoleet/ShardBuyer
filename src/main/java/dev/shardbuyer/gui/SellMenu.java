package dev.shardbuyer.gui;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.sell.SellService;
import dev.shardbuyer.util.InventoryUtil;
import dev.shardbuyer.util.ItemTemplate;
import dev.shardbuyer.util.Keys;
import dev.shardbuyer.util.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Меню продажи: кладёшь предметы и жмёшь зелёную кнопку (или просто закрываешь меню) — всё продаётся.
 * Непродаваемое возвращается в инвентарь. Шалкеры обрабатываются по настройке shulker-boxes.
 */
public final class SellMenu extends Menu {

    private int buttonSlot = -1;
    private boolean closed;
    private boolean updateScheduled;

    public SellMenu(ShardBuyer plugin, Player player) {
        super(plugin, player, null);
    }

    @Override
    protected ConfigurationSection section() {
        return plugin.menusConfig().getConfigurationSection("sell-menu");
    }

    @Override
    protected void clearForRefresh() {
        // предметы игрока не трогаем
    }

    @Override
    protected void render() {
        ConfigurationSection s = section();
        ConfigurationSection btn = s == null ? null : s.getConfigurationSection("button");
        if (btn != null && btn.getBoolean("enabled", true)) {
            buttonSlot = btn.getInt("slot", inventory.getSize() - 5);
            if (buttonSlot >= inventory.getSize()) {
                buttonSlot = inventory.getSize() - 1;
            }
        } else {
            buttonSlot = -1;
        }
        updateButton();
    }

    private List<ItemStack> contents() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == buttonSlot) {
                continue;
            }
            ItemStack is = inventory.getItem(i);
            if (is != null && !is.getType().isAir() && !Keys.isGui(is)) {
                list.add(is);
            }
        }
        return list;
    }

    private void clearContents() {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i != buttonSlot) {
                inventory.setItem(i, null);
            }
        }
    }

    private void updateButton() {
        if (buttonSlot < 0) {
            return;
        }
        ConfigurationSection btn = section().getConfigurationSection("button");
        List<ItemStack> items = contents();
        SellService.Quote q = plugin.sell().quote(player, cloneAll(items));
        int unsellable = 0;
        for (ItemStack is : q.leftovers) {
            unsellable += is.getAmount();
        }
        Placeholders ph = Ph.player(plugin, player, null);
        ph.put("total", plugin.moneyFormat().format(q.money));
        ph.put("amount", plugin.pointsFormat().format(q.amount));
        ph.put("positions", q.lines.size());
        ph.put("points_total", plugin.pointsFormat().format(q.points));
        ph.put("unsellable", unsellable);
        ph.flag("has_items", q.amount > 0);
        ph.flag("has_points", q.points > 0.0001);
        ph.flag("has_unsellable", unsellable > 0);
        ItemTemplate t = ItemTemplate.of(btn, Material.LIME_STAINED_GLASS_PANE);
        ConfigurationSection empty = btn.getConfigurationSection("empty");
        if (q.amount <= 0 && empty != null) {
            t = ItemTemplate.of(empty, t.material);
        }
        inventory.setItem(buttonSlot, t.build(ph));
    }

    private static List<ItemStack> cloneAll(List<ItemStack> items) {
        List<ItemStack> out = new ArrayList<>(items.size());
        for (ItemStack is : items) {
            out.add(is.clone());
        }
        return out;
    }

    private void scheduleUpdate() {
        if (updateScheduled) {
            return;
        }
        updateScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateScheduled = false;
            if (!closed && isViewing()) {
                updateButton();
            }
        });
    }

    private void sellAll() {
        List<ItemStack> items = cloneAll(contents());
        clearContents();
        if (items.isEmpty()) {
            plugin.messages().send(player, "sell.menu-empty");
            return;
        }
        plugin.sell().sell(player, items, SellService.Source.SELL_MENU);
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        int size = inventory.getSize();
        // клик по кнопке (в том числе цифрами/свапом)
        if (raw == buttonSlot && buttonSlot >= 0) {
            e.setCancelled(true);
            if (!plugin.menus().checkCooldown(player)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed || !isViewing()) {
                    return;
                }
                plugin.settings().soundClick.play(player);
                sellAll();
                if (plugin.settings().closeAfterButton) {
                    player.closeInventory();
                } else {
                    updateButton();
                }
            });
            return;
        }
        if (raw >= 0 && raw < size) {
            ItemStack current = e.getCurrentItem();
            if (Keys.isGui(current)) {
                e.setCancelled(true);
                return;
            }
        }
        if (e.getClick() == ClickType.DOUBLE_CLICK) {
            ItemStack cursor = e.getCursor();
            if (Keys.isGui(cursor)) {
                e.setCancelled(true);
                return;
            }
        }
        scheduleUpdate();
    }

    @Override
    public void handleDrag(InventoryDragEvent e) {
        if (buttonSlot >= 0 && e.getRawSlots().contains(buttonSlot)) {
            e.setCancelled(true);
            return;
        }
        scheduleUpdate();
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        if (closed) {
            return;
        }
        closed = true;
        List<ItemStack> items = cloneAll(contents());
        clearContents();
        if (items.isEmpty()) {
            return;
        }
        if (plugin.settings().sellOnClose) {
            plugin.sell().sell(player, items, SellService.Source.SELL_MENU);
        } else {
            InventoryUtil.give(player, items);
        }
    }

    @Override
    public void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        List<ItemStack> items = cloneAll(contents());
        clearContents();
        InventoryUtil.give(player, items);
    }
}
