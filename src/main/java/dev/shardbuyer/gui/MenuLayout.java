package dev.shardbuyer.gui;

import dev.shardbuyer.util.ItemTemplate;
import dev.shardbuyer.util.Slots;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Декор и кнопки меню из конфига (секция layout).
 * <pre>
 * layout:
 *   filler:
 *     material: BLACK_STAINED_GLASS_PANE
 *     name: " "
 *     slots: "0-8,45-53"
 *   next:
 *     slot: 53
 *     material: ARROW
 *     name: "&aДалее"
 *     action: NEXT_PAGE
 *     condition: has_next      # показывать только если условие выполнено
 *     alt: {...}               # что показать, если условие НЕ выполнено (иначе — ничего)
 *     require-shift: false     # срабатывать только по Shift+клику
 * </pre>
 */
public final class MenuLayout {

    public static final MenuLayout EMPTY = new MenuLayout(Collections.emptyList());

    public static final class Entry {
        public final String id;
        public final int[] slots;
        public final ItemTemplate item;
        public final ItemTemplate alt;
        public final String altAction;
        public final String condition;
        public final String action;
        public final boolean requireShift;

        Entry(String id, int[] slots, ItemTemplate item, ItemTemplate alt, String altAction, String condition,
              String action, boolean requireShift) {
            this.id = id;
            this.slots = slots;
            this.item = item;
            this.alt = alt;
            this.altAction = altAction;
            this.condition = condition;
            this.action = action;
            this.requireShift = requireShift;
        }
    }

    public final List<Entry> entries;

    private MenuLayout(List<Entry> entries) {
        this.entries = entries;
    }

    public static MenuLayout from(ConfigurationSection layout) {
        if (layout == null) {
            return EMPTY;
        }
        List<Entry> list = new ArrayList<>();
        for (String id : layout.getKeys(false)) {
            ConfigurationSection s = layout.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Object rawSlots = s.contains("slots") ? s.get("slots") : s.get("slot");
            int[] slots = Slots.parse(rawSlots);
            if (slots.length == 0) {
                continue;
            }
            ItemTemplate item = ItemTemplate.of(s, Material.STONE);
            ConfigurationSection altSec = s.getConfigurationSection("alt");
            ItemTemplate alt = altSec == null ? null : ItemTemplate.of(altSec, item.material);
            String altAction = altSec == null ? null : altSec.getString("action", null);
            list.add(new Entry(id, slots, item, alt, altAction, s.getString("condition", null),
                    s.getString("action", null), s.getBoolean("require-shift", false)));
        }
        return new MenuLayout(list);
    }
}
