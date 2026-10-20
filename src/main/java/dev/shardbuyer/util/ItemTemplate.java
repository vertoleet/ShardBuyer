package dev.shardbuyer.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Предмет для GUI из конфига:
 * <pre>
 * material: DIAMOND
 * amount: 1
 * name: "&bНазвание {placeholder}"   # если не указать — останется стандартное (переведённое клиентом) имя
 * lore: ["...", "[flag]строка только если flag"]
 * glow: true
 * custom-model-data: 1001
 * hide-flags: true
 * </pre>
 */
public final class ItemTemplate {

    private static Logger logger = Logger.getLogger("ShardBuyer");

    public final Material material;
    public final int amount;
    public final String name;
    public final List<String> lore;
    public final boolean glow;
    public final Integer customModelData;
    public final boolean hideFlags;

    public ItemTemplate(Material material, int amount, String name, List<String> lore, boolean glow,
                        Integer customModelData, boolean hideFlags) {
        this.material = material;
        this.amount = amount;
        this.name = name;
        this.lore = lore == null ? List.of() : lore;
        this.glow = glow;
        this.customModelData = customModelData;
        this.hideFlags = hideFlags;
    }

    public static void setLogger(Logger l) {
        logger = l;
    }

    public static ItemTemplate of(ConfigurationSection s, Material fallback) {
        if (s == null) {
            return new ItemTemplate(fallback, 1, null, List.of(), false, null, true);
        }
        Material mat = material(s.getString("material"), fallback);
        String name = s.contains("name") ? s.getString("name", "") : null;
        List<String> lore = new ArrayList<>(s.getStringList("lore"));
        Integer cmd = s.contains("custom-model-data") ? s.getInt("custom-model-data") : null;
        return new ItemTemplate(mat, Math.max(1, Math.min(64, s.getInt("amount", 1))), name, lore,
                s.getBoolean("glow", false), cmd, s.getBoolean("hide-flags", true));
    }

    public static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (m == null || !m.isItem() || m.isAir()) {
            logger.warning("Unknown material in config: " + raw + " (using " + fallback + ")");
            return fallback;
        }
        return m;
    }

    public ItemTemplate withMaterial(Material m) {
        return new ItemTemplate(m, amount, name, lore, glow, customModelData, hideFlags);
    }

    public ItemTemplate withLore(List<String> newLore) {
        return new ItemTemplate(material, amount, name, newLore, glow, customModelData, hideFlags);
    }

    public ItemTemplate withGlow(boolean g) {
        return new ItemTemplate(material, amount, name, lore, g, customModelData, hideFlags);
    }

    public ItemStack build(Placeholders ph) {
        return build(ph, new ItemStack(material, amount));
    }

    /** Применяет имя/лор/флаги к готовому предмету (например, к зачарованной книге). */
    public ItemStack build(Placeholders ph, ItemStack base) {
        Placeholders p = ph == null ? new Placeholders() : ph;
        ItemStack is = base;
        ItemMeta meta = is.getItemMeta();
        if (meta == null) {
            return is;
        }
        if (name != null) {
            String n = p.apply(name);
            meta.setDisplayName(n.isEmpty() ? " " : n);
        }
        if (!lore.isEmpty()) {
            List<String> l = p.lore(lore);
            if (!l.isEmpty()) {
                meta.setLore(l);
            }
        }
        if (glow) {
            Enchantment e = Keys.glowEnchant();
            if (e != null) {
                meta.addEnchant(e, 1, true);
            }
        }
        if (customModelData != null) {
            meta.setCustomModelData(customModelData);
        }
        if (hideFlags) {
            meta.addItemFlags(ItemFlag.values());
        }
        Keys.tagGui(meta);
        is.setItemMeta(meta);
        return is;
    }
}
