package dev.shardbuyer.gui;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.category.Category;
import dev.shardbuyer.util.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Иконка категории (в главном меню и во вкладках постраничного меню). */
final class CategoryIcons {

    private CategoryIcons() {
    }

    static Placeholders placeholders(ShardBuyer plugin, Player p, Category c) {
        Placeholders ph = Ph.player(plugin, p, c.id());
        ph.put("category", c.id());
        ph.put("category_name", c.name());
        ph.put("category_plain", dev.shardbuyer.util.Text.strip(dev.shardbuyer.util.Text.color(c.name())));
        ph.put("category_description", c.description());
        ph.put("items", c.items().size());
        return ph;
    }

    static ItemStack build(ShardBuyer plugin, Player p, Category c, boolean selected) {
        Placeholders ph = placeholders(plugin, p, c);
        ph.flag("selected", selected);
        return c.icon().withGlow(c.icon().glow || selected).build(ph);
    }
}
