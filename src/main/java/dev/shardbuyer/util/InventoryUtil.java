package dev.shardbuyer.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class InventoryUtil {

    private InventoryUtil() {
    }

    /**
     * Забирает из основного инвентаря (36 слотов) предметы, подходящие под фильтр, но не больше max штук.
     * @return список забранных предметов (копии)
     */
    public static List<ItemStack> take(Player player, Predicate<ItemStack> filter, int max) {
        List<ItemStack> taken = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int left = max <= 0 ? Integer.MAX_VALUE : max;
        boolean changed = false;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack is = contents[i];
            if (is == null || is.getType().isAir() || is.getAmount() <= 0) {
                continue;
            }
            if (!filter.test(is)) {
                continue;
            }
            int amount = is.getAmount();
            if (amount <= left) {
                taken.add(is.clone());
                contents[i] = null;
                left -= amount;
            } else {
                ItemStack part = is.clone();
                part.setAmount(left);
                taken.add(part);
                ItemStack rest = is.clone();
                rest.setAmount(amount - left);
                contents[i] = rest;
                left = 0;
            }
            changed = true;
        }
        if (changed) {
            inv.setStorageContents(contents);
        }
        return taken;
    }

    /** Выдаёт предметы игроку, то, что не влезло — выбрасывает под ноги. */
    public static void give(Player player, Collection<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack is : items) {
            if (is != null && !is.getType().isAir() && is.getAmount() > 0) {
                list.add(is);
            }
        }
        if (list.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> rest = player.getInventory().addItem(list.toArray(new ItemStack[0]));
        if (!rest.isEmpty()) {
            Location loc = player.getLocation();
            for (ItemStack is : rest.values()) {
                player.getWorld().dropItemNaturally(loc, is);
            }
        }
    }
}
