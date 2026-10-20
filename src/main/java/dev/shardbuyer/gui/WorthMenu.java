package dev.shardbuyer.gui;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.category.Category;
import dev.shardbuyer.config.Settings;
import dev.shardbuyer.price.ItemValue;
import dev.shardbuyer.util.ItemTemplate;
import dev.shardbuyer.util.Names;
import dev.shardbuyer.util.Placeholders;
import dev.shardbuyer.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /worth — все цены: страницы, сортировка, фильтр по категориям, поиск. */
public final class WorthMenu extends Menu {

    public enum Sort { PRICE_DESC, PRICE_ASC, NAME, CATEGORY }

    private static final class Entry {
        final Material material;
        final Enchantment enchant;
        final String category;
        final double price;
        final String name;

        Entry(Material material, Enchantment enchant, String category, double price, String name) {
            this.material = material;
            this.enchant = enchant;
            this.category = category;
            this.price = price;
            this.name = name;
        }
    }

    private Sort sort;
    private String filter;
    private String search;
    private int page;
    private List<Entry> entries = new ArrayList<>();

    public WorthMenu(ShardBuyer plugin, Player player, Menu parent) {
        super(plugin, player, parent);
        ConfigurationSection s = section();
        sort = Settings.enumValue(Sort.class, s == null ? null : s.getString("default-sort"), Sort.PRICE_DESC, null, "");
        rebuild();
    }

    @Override
    protected ConfigurationSection section() {
        return plugin.menusConfig().getConfigurationSection("worth-menu");
    }

    private void rebuild() {
        List<Entry> list = new ArrayList<>();
        Names names = plugin.names();
        for (Map.Entry<Material, Double> e : plugin.prices().all().entrySet()) {
            Material m = e.getKey();
            String cat = plugin.categories().categoryOf(m);
            if (filter != null && !filter.equals(cat)) {
                continue;
            }
            if (search != null && !names.matches(m, search)) {
                continue;
            }
            ItemValue v = plugin.prices().evaluate(new ItemStack(m));
            list.add(new Entry(m, null, cat, plugin.sell().unitPrice(player.getUniqueId(), v), Text.strip(names.item(m))));
        }
        ConfigurationSection s = section();
        boolean showEnchants = s == null || s.getBoolean("show-enchantments", true);
        if (showEnchants && plugin.prices().enchants().enabled()) {
            String bookCat = plugin.categories().categoryOf(Material.ENCHANTED_BOOK);
            if (filter == null || filter.equals(bookCat)) {
                for (Enchantment ench : Enchantment.values()) {
                    if (plugin.prices().enchants().value(ench, 1) <= 0) {
                        continue;
                    }
                    if (search != null && !names.matches(ench, search)) {
                        continue;
                    }
                    double price = bookPrice(ench, ench.getMaxLevel());
                    list.add(new Entry(Material.ENCHANTED_BOOK, ench, bookCat, price, Text.strip(names.enchant(ench))));
                }
            }
        }
        Comparator<Entry> cmp = switch (sort) {
            case PRICE_ASC -> Comparator.comparingDouble(e -> e.price);
            case NAME -> Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT));
            case CATEGORY -> Comparator.<Entry, Integer>comparing(e -> categoryIndex(e.category))
                    .thenComparing(e -> -e.price);
            default -> Comparator.comparingDouble(e -> -e.price);
        };
        list.sort(cmp);
        entries = list;
    }

    private int categoryIndex(String id) {
        List<Category> all = plugin.categories().all();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private ItemStack book(Enchantment e, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(e, level, true);
        book.setItemMeta(meta);
        return book;
    }

    private double bookPrice(Enchantment e, int level) {
        return plugin.sell().unitPrice(player.getUniqueId(), plugin.prices().evaluate(book(e, level)));
    }

    private int pages() {
        int per = Math.max(1, itemSlots().length);
        return Math.max(1, (entries.size() + per - 1) / per);
    }

    private List<String> filters() {
        List<String> list = new ArrayList<>();
        list.add(null);
        for (Category c : plugin.categories().visible(player)) {
            list.add(c.id());
        }
        return list;
    }

    @Override
    protected Placeholders placeholders() {
        ConfigurationSection s = section();
        Placeholders ph = Ph.player(plugin, player, filter);
        int pages = pages();
        page = Math.max(0, Math.min(page, pages - 1));
        ph.put("page", page + 1);
        ph.put("pages", pages);
        ph.put("count", entries.size());
        ph.flag("has_prev", page > 0);
        ph.flag("has_next", page < pages - 1);
        String sortName = s == null ? sort.name() : s.getString("sort-names." + sort.name(), sort.name());
        ph.put("sort", Text.color(sortName));
        String filterName = filter == null ? (s == null ? "All" : s.getString("filter-all-name", "All"))
                : plugin.categories().displayName(filter);
        ph.put("filter", Text.color(filterName));
        ph.put("search", search == null ? Text.color(s == null ? "-" : s.getString("search-none", "-")) : search);
        ph.flag("searching", search != null);
        ph.flag("filtered", filter != null);
        return ph;
    }

    @Override
    protected void render() {
        ConfigurationSection s = section();
        Placeholders ph = placeholders();
        applyLayout(MenuLayout.from(s == null ? null : s.getConfigurationSection("layout")), ph);
        int[] slots = itemSlots();
        ItemTemplate format = ItemTemplate.of(s == null ? null : s.getConfigurationSection("item-format"), Material.STONE);
        ConfigurationSection ef = s == null ? null : s.getConfigurationSection("enchant-format");
        ItemTemplate enchantFormat = ItemTemplate.of(ef, Material.ENCHANTED_BOOK);
        String levelLine = ef == null ? "&7 {level_roman}: &a${price}" : ef.getString("level-line", "&7 {level_roman}: &a${price}");
        int start = page * slots.length;
        for (int i = 0; i < slots.length; i++) {
            int idx = start + i;
            if (idx >= entries.size()) {
                break;
            }
            int slot = slots[i];
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            Entry e = entries.get(idx);
            Placeholders iph = ph.copy();
            iph.put("category_name", e.category == null ? "-" : plugin.categories().displayName(e.category));
            if (e.enchant == null) {
                ItemValue v = plugin.prices().evaluate(new ItemStack(e.material));
                double factor = plugin.market().factor(v.marketKey);
                int stack = e.material.getMaxStackSize();
                iph.put("item", plugin.names().item(e.material));
                iph.put("material", e.material.name());
                iph.put("price", plugin.moneyFormat().format(e.price));
                iph.put("price_stack", plugin.moneyFormat().format(e.price * stack));
                iph.put("stack", stack);
                iph.put("base_price", plugin.moneyFormat().format(v.unitBase));
                iph.put("market_percent", plugin.formatPercent(factor * 100));
                iph.put("market_drop", plugin.formatPercent((1 - factor) * 100));
                iph.flag("market", plugin.market().enabled() && factor < 0.9999);
                iph.flag("auto", !plugin.prices().isManual(e.material));
                iph.put("points_per_item", plugin.pointsFormat().format(v.unitPoints));
                iph.flag("leveling", plugin.levels().isActive(e.category));
                inventory.setItem(slot, format.withMaterial(e.material).build(iph));
            } else {
                StringBuilder levels = new StringBuilder();
                for (int lvl = 1; lvl <= Math.max(1, e.enchant.getMaxLevel()); lvl++) {
                    if (levels.length() > 0) {
                        levels.append('\n');
                    }
                    levels.append(Text.color(levelLine
                            .replace("{level}", String.valueOf(lvl))
                            .replace("{level_roman}", Names.roman(lvl))
                            .replace("{price}", plugin.moneyFormat().format(bookPrice(e.enchant, lvl)))));
                }
                iph.put("enchant", plugin.names().enchant(e.enchant));
                iph.put("item", plugin.names().enchant(e.enchant));
                iph.put("max_level", Names.roman(e.enchant.getMaxLevel()));
                iph.put("price", plugin.moneyFormat().format(e.price));
                iph.put("levels", levels.toString());
                inventory.setItem(slot, enchantFormat.build(iph, book(e.enchant, e.enchant.getMaxLevel())));
            }
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
            case "SORT" -> {
                Sort[] all = Sort.values();
                int i = sort.ordinal() + (click.isRightClick() ? -1 : 1);
                sort = all[(i + all.length) % all.length];
                page = 0;
                rebuild();
                refresh();
                return true;
            }
            case "FILTER" -> {
                List<String> f = filters();
                int i = f.indexOf(filter) + (click.isRightClick() ? -1 : 1);
                filter = f.get((i + f.size()) % f.size());
                page = 0;
                rebuild();
                refresh();
                return true;
            }
            case "SEARCH" -> {
                player.closeInventory();
                plugin.messages().send(player, "worth.search-prompt");
                plugin.chatInput().await(player, text -> {
                    if (text == null) {
                        plugin.messages().send(player, "worth.search-cancelled");
                    } else {
                        search = text.trim().isEmpty() ? null : text.trim();
                        page = 0;
                        rebuild();
                    }
                    open();
                });
                return true;
            }
            case "CLEAR_SEARCH" -> {
                search = null;
                page = 0;
                rebuild();
                refresh();
                return true;
            }
            case "CLEAR_FILTER" -> {
                filter = null;
                search = null;
                page = 0;
                rebuild();
                refresh();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Открыть с готовым фильтром категории. */
    public WorthMenu filter(String categoryId) {
        this.filter = categoryId;
        rebuild();
        return this;
    }

    public WorthMenu search(String text) {
        this.search = text;
        rebuild();
        return this;
    }
}
