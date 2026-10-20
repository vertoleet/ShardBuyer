package dev.shardbuyer.hook;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.config.Settings;
import dev.shardbuyer.sell.SellService;
import dev.shardbuyer.util.Keys;
import dev.shardbuyer.util.Placeholders;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Цена в описании предмета — ТОЛЬКО на стороне клиента.
 * <p>
 * Мы не меняем настоящие предметы на сервере: строки с ценой дописываются в пакеты SET_SLOT / WINDOW_ITEMS,
 * которые уходят конкретному игроку. Поэтому:
 * <ul>
 *     <li>цену видит только сам игрок (и по его личным бонусам);</li>
 *     <li>предметы на сервере остаются одинаковыми и нормально стакаются;</li>
 *     <li>цена пересчитывается при каждом изменении инвентаря (сервер сам пересылает слот).</li>
 * </ul>
 * В креативе клиент может «вернуть» предмет с лором на сервер, поэтому там цена по умолчанию не показывается,
 * а на всякий случай лишние строки срезаются в InventoryCreativeEvent.
 */
public final class PriceLoreHook implements Listener {

    private final ShardBuyer plugin;
    private PacketAdapter adapter;
    private volatile boolean errorLogged;

    public PriceLoreHook(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    public void register() {
        adapter = new PacketAdapter(plugin, ListenerPriority.HIGH,
                PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    handle(event);
                } catch (Throwable t) {
                    if (!errorLogged) {
                        errorLogged = true;
                        plugin.getLogger().warning("Price lore error: " + t);
                    }
                }
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        if (adapter != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
            adapter = null;
        }
    }

    private boolean shouldShow(Player p) {
        Settings s = plugin.settings();
        if (!s.loreEnabled) {
            return false;
        }
        if (!s.loreInCreative && p.getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        return plugin.loreEnabled(p);
    }

    private void handle(PacketEvent event) {
        if (event.isPlayerTemporary()) {
            return;
        }
        Player p = event.getPlayer();
        if (p == null || !shouldShow(p)) {
            return;
        }
        PacketContainer packet = event.getPacket();
        int window = packet.getIntegers().read(0);
        if (!plugin.settings().loreInContainers && window != 0 && window != -1 && window != -2) {
            return;
        }
        UUID uuid = p.getUniqueId();
        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            ItemStack is = packet.getItemModifier().read(0);
            ItemStack out = decorate(uuid, is);
            if (out != is) {
                packet.getItemModifier().write(0, out);
            }
        } else {
            List<ItemStack> list = packet.getItemListModifier().read(0);
            if (list != null) {
                List<ItemStack> copy = new ArrayList<>(list.size());
                boolean changed = false;
                for (ItemStack is : list) {
                    ItemStack out = decorate(uuid, is);
                    changed |= out != is;
                    copy.add(out);
                }
                if (changed) {
                    packet.getItemListModifier().write(0, copy);
                }
            }
            ItemStack carried = packet.getItemModifier().readSafely(0);
            if (carried != null) {
                ItemStack out = decorate(uuid, carried);
                if (out != carried) {
                    packet.getItemModifier().write(0, out);
                }
            }
        }
    }

    private ItemStack decorate(UUID player, ItemStack is) {
        if (is == null || is.getType().isAir() || is.getAmount() <= 0) {
            return is;
        }
        if (Keys.isGui(is)) {
            return is;
        }
        double total = plugin.sell().displayValue(player, is);
        if (total <= 0) {
            return is;
        }
        int amount = is.getAmount();
        boolean shulker = SellService.isShulkerWithContents(is);
        Placeholders ph = new Placeholders();
        ph.put("price", plugin.moneyFormat().format(total));
        ph.put("price_one", plugin.moneyFormat().format(total / amount));
        ph.put("amount", amount);
        ph.flag("stack", amount > 1);
        ph.flag("shulker", shulker);
        List<String> add = ph.lore(plugin.settings().loreLines);
        if (add.isEmpty()) {
            return is;
        }
        ItemStack copy = is.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return is;
        }
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.addAll(add);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(Keys.LORE_LINES, PersistentDataType.INTEGER, add.size());
        copy.setItemMeta(meta);
        return copy;
    }

    /** Если креатив-клиент прислал предмет с нашим лором — срезаем его. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreative(InventoryCreativeEvent e) {
        ItemStack cursor = e.getCursor();
        ItemStack clean = strip(cursor);
        if (clean != cursor) {
            e.setCursor(clean);
        }
    }

    public static ItemStack strip(ItemStack is) {
        if (is == null || !is.hasItemMeta()) {
            return is;
        }
        ItemMeta meta = is.getItemMeta();
        if (meta == null) {
            return is;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer lines = pdc.get(Keys.LORE_LINES, PersistentDataType.INTEGER);
        if (lines == null) {
            return is;
        }
        ItemStack copy = is.clone();
        ItemMeta m = copy.getItemMeta();
        List<String> lore = m.getLore();
        if (lore != null) {
            List<String> l = new ArrayList<>(lore);
            for (int i = 0; i < lines && !l.isEmpty(); i++) {
                l.remove(l.size() - 1);
            }
            m.setLore(l.isEmpty() ? null : l);
        }
        m.getPersistentDataContainer().remove(Keys.LORE_LINES);
        copy.setItemMeta(m);
        return copy;
    }
}
