package dev.shardbuyer.gui;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.category.Category;
import dev.shardbuyer.util.ItemTemplate;
import dev.shardbuyer.util.Placeholders;
import dev.shardbuyer.util.Slots;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * Меню одной категории (режим CATEGORIES). Берёт настройки из categories.yml -> &lt;id&gt;.menu,
 * а всё, что там не указано — из menus.yml -> category-menu.
 */
public final class CategoryMenu extends Menu {

    private final Category category;
    private int page;

    public CategoryMenu(ShardBuyer plugin, Player player, Menu parent, Category category) {
        super(plugin, player, parent);
        this.category = category;
    }

    private ConfigurationSection defaults() {
        return plugin.menusConfig().getConfigurationSection("category-menu");
    }

    /** Секция, в которой есть ключ: своя у категории или общая. */
    private ConfigurationSection pick(String key) {
        ConfigurationSection own = category.menu();
        if (own != null && own.contains(key)) {
            return own;
        }
        return defaults();
    }

    @Override
    protected ConfigurationSection section() {
        return pick("title");
    }

    @Override
    protected int size() {
        ConfigurationSection s = pick("size");
        int size = s == null ? 54 : s.getInt("size", 54);
        size = Math.max(9, Math.min(54, size));
        return size - size % 9;
    }

    @Override
    protected int[] itemSlots() {
        ConfigurationSection s = pick("item-slots");
        int[] slots = s == null ? new int[0] : Slots.parse(s.get("item-slots"));
        return slots.length == 0 ? Slots.parse("10-16,19-25,28-34,37-43") : slots;
    }

    private int pages() {
        int per = Math.max(1, itemSlots().length);
        return Math.max(1, (category.items().size() + per - 1) / per);
    }

    @Override
    protected Placeholders placeholders() {
        Placeholders ph = Ph.player(plugin, player, category.id());
        int pages = pages();
        page = Math.max(0, Math.min(page, pages - 1));
        ph.put("category", category.id());
        ph.put("category_name", category.name());
        ph.put("category_plain", dev.shardbuyer.util.Text.strip(dev.shardbuyer.util.Text.color(category.name())));
        ph.put("category_description", category.description());
        ph.put("page", page + 1);
        ph.put("pages", pages);
        ph.put("items", category.items().size());
        ph.flag("has_prev", page > 0);
        ph.flag("has_next", page < pages - 1);
        return ph;
    }

    @Override
    protected void render() {
        Placeholders ph = placeholders();
        ConfigurationSection layoutHolder = pick("layout");
        applyLayout(MenuLayout.from(layoutHolder == null ? null : layoutHolder.getConfigurationSection("layout")), ph);
        int[] slots = itemSlots();
        ItemTemplate format = ShopItems.format(plugin, pick("item-format"));
        ShopItems.Snapshot snap = ShopItems.snapshot(plugin, player);
        List<Material> items = category.items();
        int start = page * slots.length;
        for (int i = 0; i < slots.length; i++) {
            int idx = start + i;
            int slot = slots[i];
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            if (idx >= items.size()) {
                break;
            }
            Material m = items.get(idx);
            inventory.setItem(slot, ShopItems.icon(plugin, player, m, format, snap, ph));
            buttons.put(slot, click -> {
                if (ShopItems.click(plugin, player, m, click)) {
                    refresh();
                }
            });
        }
    }

    @Override
    protected boolean onAction(String type, String arg, ClickType click) {
        switch (type) {
            case "NEXT_PAGE" -> {
                if (page < pages() - 1) {
                    page++;
                    refresh();
                }
                return true;
            }
            case "PREV_PAGE" -> {
                if (page > 0) {
                    page--;
                    refresh();
                }
                return true;
            }
            case "SELL_CATEGORY" -> {
                ShopItems.sellCategory(plugin, player, category.id(), category.items());
                refresh();
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
