package dev.shardbuyer.category;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.config.Settings;
import dev.shardbuyer.util.ItemTemplate;
import dev.shardbuyer.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Загружает категории и распределяет по ним все предметы с ценой. */
public final class CategoryManager {

    private final ShardBuyer plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private volatile Map<Material, String> byMaterial = Collections.emptyMap();

    public CategoryManager(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    public void load() {
        categories.clear();
        YamlConfiguration cfg = plugin.files().load("categories.yml", false);
        ConfigurationSection root = cfg.getConfigurationSection("categories");
        if (root == null) {
            plugin.getLogger().warning("categories.yml: section 'categories' is missing");
            return;
        }
        ConfigurationSection lang = plugin.menusConfig().getConfigurationSection("categories");
        ConfigurationSection iconTemplate = plugin.menusConfig().getConfigurationSection("category-icon");
        ConfigurationSection langMenus = plugin.menusConfig().getConfigurationSection("category-menus");
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(rawId);
            if (s == null || !s.getBoolean("enabled", true)) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection text = lang == null ? null : lang.getConfigurationSection(rawId);
            // название: categories.yml -> name, иначе из языкового файла, иначе id
            String name = s.getString("name", text == null ? id : text.getString("name", id));
            List<String> description = s.isList("description") ? s.getStringList("description")
                    : (text == null ? List.of() : text.getStringList("description"));
            // иконка: материал/вид из categories.yml, текст (если не задан там) — из menus.yml -> category-icon
            ConfigurationSection iconSec = s.getConfigurationSection("icon");
            Material mat = ItemTemplate.material(iconSec == null ? null : iconSec.getString("material"), Material.CHEST);
            ItemTemplate base = ItemTemplate.of(iconTemplate, mat).withMaterial(mat);
            ItemTemplate icon = iconSec == null ? base : ItemTemplate.of(iconSec, mat);
            if (iconSec != null && !iconSec.contains("name")) {
                icon = new ItemTemplate(icon.material, icon.amount, base.name, icon.lore, icon.glow, icon.customModelData, icon.hideFlags);
            }
            if (iconSec == null || !iconSec.contains("lore")) {
                icon = icon.withLore(base.lore);
            }
            Category.Sort sort = Settings.enumValue(Category.Sort.class, s.getString("sort"), Category.Sort.CONFIG,
                    plugin.getLogger(), "categories." + id + ".sort");
            String perm = s.getString("permission", "");
            ConfigurationSection menu = s.getConfigurationSection("menu");
            if (menu == null && langMenus != null) {
                menu = langMenus.getConfigurationSection(rawId);
            }
            String desc = Text.replace(String.join("\n", description), plugin.commandPlaceholders());
            categories.put(id, new Category(id, name, desc, icon,
                    s.getInt("slot", -1), s.getBoolean("leveling", true),
                    new ArrayList<>(s.getStringList("items")),
                    menu, s.getConfigurationSection("levels"), sort,
                    perm == null || perm.isBlank() ? null : perm));
        }
    }

    /** Распределение предметов по категориям. Вызывать после пересчёта цен. */
    public void resolve() {
        Set<Material> priced = plugin.prices().all().keySet();
        List<Material> sortedPriced = new ArrayList<>(priced);
        sortedPriced.sort((a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
        Map<Material, String> map = new EnumMap<>(Material.class);
        Map<String, List<Material>> lists = new HashMap<>();
        for (Category c : categories.values()) {
            lists.put(c.id(), new ArrayList<>());
        }
        // 1) точные названия
        for (Category c : categories.values()) {
            for (String p : c.patterns()) {
                if (isWildcard(p)) {
                    continue;
                }
                Material m = Material.matchMaterial(p.trim().toUpperCase(Locale.ROOT));
                if (m == null) {
                    plugin.getLogger().warning("categories.yml (" + c.id() + "): unknown item " + p);
                    continue;
                }
                if (priced.contains(m) && !map.containsKey(m)) {
                    map.put(m, c.id());
                    lists.get(c.id()).add(m);
                }
            }
        }
        // 2) шаблоны вида *_LOG, RAW_*
        for (Category c : categories.values()) {
            for (String p : c.patterns()) {
                if (!isWildcard(p) || p.trim().equals("*")) {
                    continue;
                }
                Pattern rx = glob(p.trim());
                for (Material m : sortedPriced) {
                    if (!map.containsKey(m) && rx.matcher(m.name()).matches()) {
                        map.put(m, c.id());
                        lists.get(c.id()).add(m);
                    }
                }
            }
        }
        // 3) "*" — всё остальное
        for (Category c : categories.values()) {
            if (c.patterns().stream().anyMatch(p -> p.trim().equals("*"))) {
                for (Material m : sortedPriced) {
                    if (!map.containsKey(m)) {
                        map.put(m, c.id());
                        lists.get(c.id()).add(m);
                    }
                }
            }
        }
        for (Category c : categories.values()) {
            List<Material> l = lists.get(c.id());
            switch (c.sort()) {
                case PRICE_DESC -> l.sort((a, b) -> Double.compare(plugin.prices().base(b), plugin.prices().base(a)));
                case PRICE_ASC -> l.sort((a, b) -> Double.compare(plugin.prices().base(a), plugin.prices().base(b)));
                case NAME -> l.sort((a, b) -> Text.strip(plugin.names().item(a)).compareToIgnoreCase(Text.strip(plugin.names().item(b))));
                default -> {
                }
            }
            c.setItems(l);
        }
        byMaterial = Collections.unmodifiableMap(map);
    }

    private static boolean isWildcard(String p) {
        return p.contains("*") || p.contains("?");
    }

    private static Pattern glob(String p) {
        StringBuilder sb = new StringBuilder();
        for (char ch : p.toUpperCase(Locale.ROOT).toCharArray()) {
            switch (ch) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> sb.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return Pattern.compile(sb.toString());
    }

    public String categoryOf(Material m) {
        return byMaterial.get(m);
    }

    public Category get(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Category> all() {
        return new ArrayList<>(categories.values());
    }

    /** Категории, доступные игроку и не пустые. */
    public List<Category> visible(Player p) {
        List<Category> out = new ArrayList<>();
        for (Category c : categories.values()) {
            if (c.items().isEmpty()) {
                continue;
            }
            if (c.permission() != null && !p.hasPermission(c.permission())) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    public String displayName(String id) {
        Category c = get(id);
        return c == null ? "" : c.name();
    }
}
