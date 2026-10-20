package dev.shardbuyer.category;

import dev.shardbuyer.util.ItemTemplate;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Категория скупщика из categories.yml. */
public final class Category {

    public enum Sort { CONFIG, PRICE_DESC, PRICE_ASC, NAME }

    private final String id;
    private final String name;
    private final String description;
    private final ItemTemplate icon;
    private final int slot;
    private final boolean leveling;
    private final List<String> patterns;
    private final ConfigurationSection menu;
    private final ConfigurationSection levels;
    private final Sort sort;
    private final String permission;
    private volatile List<Material> items = Collections.emptyList();

    public Category(String id, String name, String description, ItemTemplate icon, int slot, boolean leveling, List<String> patterns,
                    ConfigurationSection menu, ConfigurationSection levels, Sort sort, String permission) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.slot = slot;
        this.leveling = leveling;
        this.patterns = patterns;
        this.menu = menu;
        this.levels = levels;
        this.sort = sort;
        this.permission = permission;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Описание (несколько строк через \n) — плейсхолдер {category_description}. */
    public String description() {
        return description;
    }

    public ItemTemplate icon() {
        return icon;
    }

    public int slot() {
        return slot;
    }

    public boolean leveling() {
        return leveling;
    }

    public List<String> patterns() {
        return patterns;
    }

    /** Своё меню категории (может быть null — тогда берётся category-menu из menus.yml). */
    public ConfigurationSection menu() {
        return menu;
    }

    /** Свои уровни (только для режима PER_CATEGORY), может быть null. */
    public ConfigurationSection levels() {
        return levels;
    }

    public Sort sort() {
        return sort;
    }

    public String permission() {
        return permission;
    }

    public List<Material> items() {
        return items;
    }

    void setItems(List<Material> list) {
        this.items = Collections.unmodifiableList(new ArrayList<>(list));
    }
}
