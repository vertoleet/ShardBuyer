package dev.shardbuyer.gui;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.exclusive.ExclusiveManager;
import dev.shardbuyer.util.ItemTemplate;
import dev.shardbuyer.util.Placeholders;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Locale;

/** Меню эксклюзивных предметов с таймером обновления. */
public final class ExclusiveMenu extends Menu implements MenuManager.Ticking {

    public ExclusiveMenu(ShardBuyer plugin, Player player, Menu parent) {
        super(plugin, player, parent);
    }

    @Override
    protected ConfigurationSection section() {
        return plugin.menusConfig().getConfigurationSection("exclusive-menu");
    }

    @Override
    protected Placeholders placeholders() {
        Placeholders ph = Ph.player(plugin, player, null);
        ph.put("time_left", plugin.timeFormat().format(plugin.exclusive().timeLeft()));
        ph.put("count", plugin.exclusive().currentItems().size());
        ph.put("limit_mode", plugin.messages().get("exclusive.limit-mode." + plugin.exclusive().limitMode().name().toLowerCase(Locale.ROOT)));
        return ph;
    }

    @Override
    protected void render() {
        ConfigurationSection s = section();
        Placeholders ph = placeholders();
        applyLayout(MenuLayout.from(s == null ? null : s.getConfigurationSection("layout")), ph);
        int[] slots = itemSlots();
        ItemTemplate format = ItemTemplate.of(s == null ? null : s.getConfigurationSection("item-format"), Material.STONE);
        List<ExclusiveManager.Item> items = plugin.exclusive().currentItems();
        ExclusiveManager ex = plugin.exclusive();
        String category;
        for (int i = 0; i < slots.length && i < items.size(); i++) {
            int slot = slots[i];
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            ExclusiveManager.Item it = items.get(i);
            category = plugin.categories().categoryOf(it.material);
            double mult = ex.applyLevelBoost() ? plugin.sell().multiplier(plugin.data().get(player), category)
                    : plugin.data().get(player).permMultiplier() * plugin.booster().multiplier();
            int left = ex.remaining(player.getUniqueId(), it);
            Placeholders iph = ph.copy();
            iph.put("item", plugin.names().item(it.material));
            iph.put("price", plugin.moneyFormat().format(it.price * mult));
            iph.put("base_price", plugin.moneyFormat().format(it.price));
            iph.put("points_per_item", plugin.pointsFormat().format(it.points));
            iph.put("left", left);
            iph.put("limit", it.limit);
            iph.put("sold", it.limit - left);
            iph.put("price_left", plugin.moneyFormat().format(it.price * mult * left));
            iph.flag("available", left > 0);
            iph.flag("give_points", ex.givePoints() && it.points > 0 && plugin.levels().isActive(category));
            inventory.setItem(slot, format.withMaterial(it.material).withGlow(format.glow && left > 0).build(iph));
            buttons.put(slot, click -> {
                int max = switch (click) {
                    case LEFT -> 0;
                    case RIGHT -> it.material.getMaxStackSize();
                    case SHIFT_LEFT, SHIFT_RIGHT -> 1;
                    default -> -1;
                };
                if (max < 0) {
                    return;
                }
                plugin.sell().sellExclusive(player, it, max);
                refresh();
            });
        }
    }

    @Override
    protected boolean onAction(String type, String arg, ClickType click) {
        return false;
    }

    @Override
    public void tick() {
        refresh();
    }
}
