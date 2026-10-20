package dev.shardbuyer.gui;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.config.Settings;
import dev.shardbuyer.data.PlayerData;
import dev.shardbuyer.price.ItemValue;
import dev.shardbuyer.util.ItemTemplate;
import dev.shardbuyer.util.Placeholders;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Иконки предметов в меню категорий и их клики (продать / автопродажа). */
public final class ShopItems {

    /** Что лежит у игрока в инвентаре (только то, что можно быстро продать). */
    public static final class Snapshot {
        final Map<Material, long[]> amounts = new EnumMap<>(Material.class);
        final Map<Material, double[]> values = new EnumMap<>(Material.class);

        public long amount(Material m) {
            long[] a = amounts.get(m);
            return a == null ? 0 : a[0];
        }

        public double value(Material m) {
            double[] v = values.get(m);
            return v == null ? 0 : v[0];
        }
    }

    private ShopItems() {
    }

    public static Snapshot snapshot(ShardBuyer plugin, Player p) {
        Snapshot s = new Snapshot();
        for (ItemStack is : p.getInventory().getStorageContents()) {
            if (is == null || is.getType().isAir()) {
                continue;
            }
            ItemValue v = plugin.prices().evaluate(is);
            if (!v.sellable || (plugin.settings().protectSpecialItems && v.special)) {
                continue;
            }
            s.amounts.computeIfAbsent(is.getType(), k -> new long[1])[0] += is.getAmount();
            s.values.computeIfAbsent(is.getType(), k -> new double[1])[0] += plugin.sell().unitPrice(p.getUniqueId(), v) * is.getAmount();
        }
        return s;
    }

    /** Формат иконки: секция item-format меню, либо общая item-format из menus.yml. */
    public static ItemTemplate format(ShardBuyer plugin, ConfigurationSection menuSection) {
        ConfigurationSection f = menuSection == null ? null : menuSection.getConfigurationSection("item-format");
        if (f == null) {
            f = plugin.menusConfig().getConfigurationSection("item-format");
        }
        return ItemTemplate.of(f, Material.STONE);
    }

    public static ItemStack icon(ShardBuyer plugin, Player p, Material m, ItemTemplate format, Snapshot snap, Placeholders base) {
        PlayerData d = plugin.data().get(p);
        ItemValue v = plugin.prices().evaluate(new ItemStack(m));
        double unit = plugin.sell().unitPrice(p.getUniqueId(), v);
        double baseUnit = v.unitBase;
        double factor = plugin.market().factor(v.marketKey);
        int stack = m.getMaxStackSize();
        Placeholders ph = base.copy();
        ph.put("item", plugin.names().item(m));
        ph.put("material", m.name());
        ph.put("price", plugin.moneyFormat().format(unit));
        ph.put("price_stack", plugin.moneyFormat().format(unit * stack));
        ph.put("stack", stack);
        ph.put("base_price", plugin.moneyFormat().format(baseUnit));
        long amount = snap.amount(m);
        ph.put("amount", plugin.pointsFormat().format(amount));
        ph.put("price_inventory", plugin.moneyFormat().format(snap.value(m)));
        ph.flag("has_items", amount > 0);
        ph.put("market_percent", plugin.formatPercent(factor * 100));
        ph.put("market_drop", plugin.formatPercent((1 - factor) * 100));
        ph.flag("market", plugin.market().enabled() && factor < 0.9999);
        ph.put("points_per_item", plugin.pointsFormat().format(v.unitPoints * (plugin.market().affectsPoints() ? factor : 1)));
        String catId = plugin.categories().categoryOf(m);
        ph.flag("leveling", plugin.levels().isActive(catId));
        Settings s = plugin.settings();
        boolean autoAvail = s.autosellEnabled && p.hasPermission("shardbuyer.autosell");
        boolean autoOn = d.autosell().contains(m);
        ph.flag("autosell_available", autoAvail);
        ph.flag("autosell_item", autoOn);
        ph.put("autosell_item_status", Ph.status(plugin, autoOn));
        ItemTemplate t = format.withMaterial(m);
        if (autoAvail && autoOn && s.autosellGlow) {
            t = t.withGlow(true);
        }
        return t.build(ph);
    }

    /** @return true если меню нужно перерисовать */
    public static boolean click(ShardBuyer plugin, Player p, Material m, ClickType click) {
        Settings s = plugin.settings();
        Settings.ClickAction action = s.clickActions.getOrDefault(click, Settings.ClickAction.NONE);
        Object itemName = plugin.names().itemArg(m);
        switch (action) {
            case SELL_ALL -> sell(plugin, p, m, 0, itemName);
            case SELL_STACK -> sell(plugin, p, m, m.getMaxStackSize(), itemName);
            case SELL_ONE -> sell(plugin, p, m, 1, itemName);
            case TOGGLE_AUTOSELL -> {
                if (!s.autosellEnabled) {
                    plugin.messages().send(p, "autosell.disabled");
                    return false;
                }
                if (!p.hasPermission("shardbuyer.autosell")) {
                    plugin.messages().send(p, "no-permission");
                    return false;
                }
                boolean on = plugin.data().get(p).toggleAutosell(m);
                plugin.messages().send(p, on ? "autosell.item-enabled" : "autosell.item-disabled", "item", itemName);
                s.soundToggle.play(p);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void sell(ShardBuyer plugin, Player p, Material m, int max, Object itemName) {
        plugin.sell().sellFromInventory(p, is -> is.getType() == m && plugin.sell().quickSellable(is), max,
                dev.shardbuyer.sell.SellService.Source.MENU, "sell.nothing-item", "item", itemName);
    }

    /** Продать все предметы категории из инвентаря. */
    public static void sellCategory(ShardBuyer plugin, Player p, String categoryId, List<Material> items) {
        plugin.sell().sellFromInventory(p, is -> items.contains(is.getType()) && plugin.sell().quickSellable(is), 0,
                dev.shardbuyer.sell.SellService.Source.MENU, "sell.nothing-category",
                "category", plugin.categories().displayName(categoryId));
    }
}
