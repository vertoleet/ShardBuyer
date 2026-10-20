package dev.shardbuyer.gui;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.category.Category;
import dev.shardbuyer.util.Placeholders;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Главное меню (режим CATEGORIES): иконки категорий + кнопки. */
public final class MainMenu extends Menu {

    public MainMenu(ShardBuyer plugin, Player player) {
        super(plugin, player, null);
    }

    @Override
    protected ConfigurationSection section() {
        return plugin.menusConfig().getConfigurationSection("main-menu");
    }

    @Override
    protected void render() {
        Placeholders ph = placeholders();
        applyLayout(MenuLayout.from(section() == null ? null : section().getConfigurationSection("layout")), ph);
        int size = inventory.getSize();
        for (Category c : plugin.categories().visible(player)) {
            if (c.slot() < 0 || c.slot() >= size) {
                continue;
            }
            inventory.setItem(c.slot(), CategoryIcons.build(plugin, player, c, false));
            buttons.put(c.slot(), click -> plugin.menus().openCategory(player, c.id(), this));
        }
    }
}
