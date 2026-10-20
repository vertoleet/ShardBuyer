package dev.shardbuyer.util;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Ключи PersistentDataContainer, используемые плагином. */
public final class Keys {

    public static NamespacedKey GUI;
    public static NamespacedKey LORE_LINES;
    private static Enchantment glow;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        GUI = new NamespacedKey(plugin, "gui");
        LORE_LINES = new NamespacedKey(plugin, "price_lore");
        glow = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
    }

    public static Enchantment glowEnchant() {
        return glow;
    }

    public static void tagGui(ItemMeta meta) {
        meta.getPersistentDataContainer().set(GUI, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isGui(ItemMeta meta) {
        return meta != null && meta.getPersistentDataContainer().has(GUI, PersistentDataType.BYTE);
    }

    public static boolean isGui(ItemStack is) {
        return is != null && is.hasItemMeta() && isGui(is.getItemMeta());
    }
}
