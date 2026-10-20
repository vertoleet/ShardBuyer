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

import java.util.ArrayList;
import java.util.List;

/**
 * Режим PAGED: одно меню, страницы перелистываются по категориям.
 * Если в категории больше предметов, чем слотов, она занимает несколько страниц.
 * Сверху (опционально) — вкладки категорий для быстрого перехода.
 */
public final class PagedMenu extends Menu {

    private static final class Page {
        final Category category;
        final int index;
        final int count;

        Page(Category category, int index, int count) {
            this.category = category;
            this.index = index;
            this.count = count;
        }
    }

    private List<Page> pages = new ArrayList<>();
    private int page;

    public PagedMenu(ShardBuyer plugin, Player player, Menu parent, String startCategory) {
        super(plugin, player, parent);
        buildPages();
        if (startCategory != null) {
            jumpIndex(startCategory);
        }
    }

    @Override
    protected ConfigurationSection section() {
        return plugin.menusConfig().getConfigurationSection("paged-menu");
    }

    private void buildPages() {
        List<Page> list = new ArrayList<>();
        int per = Math.max(1, itemSlots().length);
        for (Category c : plugin.categories().visible(player)) {
            int count = Math.max(1, (c.items().size() + per - 1) / per);
            for (int i = 0; i < count; i++) {
                list.add(new Page(c, i, count));
            }
        }
        pages = list;
    }

    private void jumpIndex(String categoryId) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).category.id().equalsIgnoreCase(categoryId)) {
                page = i;
                return;
            }
        }
    }

    public void jumpTo(String categoryId) {
        jumpIndex(categoryId);
        refresh();
    }

    private Page current() {
        if (pages.isEmpty()) {
            return null;
        }
        page = Math.max(0, Math.min(page, pages.size() - 1));
        return pages.get(page);
    }

    @Override
    protected Placeholders placeholders() {
        Page p = current();
        Placeholders ph = Ph.player(plugin, player, p == null ? null : p.category.id());
        ph.put("page", page + 1);
        ph.put("pages", Math.max(1, pages.size()));
        ph.put("category", p == null ? "" : p.category.id());
        ph.put("category_name", p == null ? "" : p.category.name());
        ph.put("category_plain", dev.shardbuyer.util.Text.strip(dev.shardbuyer.util.Text.color(p == null ? "" : p.category.name())));
        ph.put("category_description", p == null ? "" : p.category.description());
        ph.put("category_page", p == null ? 1 : p.index + 1);
        ph.put("category_pages", p == null ? 1 : p.count);
        ph.put("items", p == null ? 0 : p.category.items().size());
        ph.flag("has_prev", page > 0);
        ph.flag("has_next", page < pages.size() - 1);
        Category prev = page > 0 ? pages.get(page - 1).category : null;
        Category next = page < pages.size() - 1 ? pages.get(page + 1).category : null;
        ph.put("prev_category", prev == null ? "" : prev.name());
        ph.put("next_category", next == null ? "" : next.name());
        return ph;
    }

    @Override
    protected void render() {
        ConfigurationSection sec = section();
        Placeholders ph = placeholders();
        applyLayout(MenuLayout.from(sec == null ? null : sec.getConfigurationSection("layout")), ph);
        Page p = current();
        if (p == null) {
            return;
        }
        renderTabs(sec, p);
        int[] slots = itemSlots();
        ItemTemplate format = ShopItems.format(plugin, sec);
        ShopItems.Snapshot snap = ShopItems.snapshot(plugin, player);
        List<Material> items = p.category.items();
        int start = p.index * slots.length;
        for (int i = 0; i < slots.length; i++) {
            int idx = start + i;
            int slot = slots[i];
            if (idx >= items.size()) {
                break;
            }
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
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

    private void renderTabs(ConfigurationSection sec, Page current) {
        ConfigurationSection tabs = sec == null ? null : sec.getConfigurationSection("category-tabs");
        if (tabs == null || !tabs.getBoolean("enabled", true)) {
            return;
        }
        int[] slots = Slots.parse(tabs.get("slots"));
        if (slots.length == 0) {
            return;
        }
        List<Category> cats = plugin.categories().visible(player);
        int curIdx = cats.indexOf(current.category);
        int start = 0;
        if (cats.size() > slots.length) {
            start = Math.max(0, Math.min(curIdx - slots.length / 2, cats.size() - slots.length));
        }
        for (int i = 0; i < slots.length && start + i < cats.size(); i++) {
            Category c = cats.get(start + i);
            int slot = slots[i];
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, CategoryIcons.build(plugin, player, c, c == current.category));
            buttons.put(slot, click -> jumpTo(c.id()));
        }
    }

    @Override
    protected boolean onAction(String type, String arg, ClickType click) {
        switch (type) {
            case "NEXT_PAGE" -> {
                if (page < pages.size() - 1) {
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
            case "NEXT_CATEGORY", "PREV_CATEGORY" -> {
                Page p = current();
                if (p == null) {
                    return true;
                }
                List<Category> cats = plugin.categories().visible(player);
                int i = cats.indexOf(p.category) + (type.equals("NEXT_CATEGORY") ? 1 : -1);
                if (i >= 0 && i < cats.size()) {
                    jumpTo(cats.get(i).id());
                }
                return true;
            }
            case "SELL_CATEGORY" -> {
                Page p = current();
                if (p != null) {
                    ShopItems.sellCategory(plugin, player, p.category.id(), p.category.items());
                    refresh();
                }
                return true;
            }
            case "OPEN_CATEGORY" -> {
                jumpTo(arg);
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
