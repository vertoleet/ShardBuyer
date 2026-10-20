package dev.shardbuyer.price;

import org.bukkit.Material;

/** Результат оценки одного предмета (за 1 штуку, без бонусов игрока). */
public final class ItemValue {

    public enum Reason { OK, EMPTY, NO_PRICE, DISABLED, RENAMED, LORE, CUSTOM_MODEL, CONTAINER_NOT_EMPTY, GUI }

    public static final ItemValue EMPTY = new ItemValue(false, null, 0, 0, null, null, false, Reason.EMPTY);

    public final boolean sellable;
    public final Material material;
    /** базовая цена за 1 шт. (с учётом зачарований и прочности) */
    public final double unitBase;
    /** очки за 1 шт. (до множителя рынка) */
    public final double unitPoints;
    /** ключ для динамического рынка */
    public final String marketKey;
    /** id категории или null */
    public final String category;
    /** зачарован / повреждён / переименован — такие предметы защищены от «быстрой» продажи */
    public final boolean special;
    public final Reason reason;

    public ItemValue(boolean sellable, Material material, double unitBase, double unitPoints, String marketKey,
                     String category, boolean special, Reason reason) {
        this.sellable = sellable;
        this.material = material;
        this.unitBase = unitBase;
        this.unitPoints = unitPoints;
        this.marketKey = marketKey;
        this.category = category;
        this.special = special;
        this.reason = reason;
    }

    public static ItemValue unsellable(Material m, Reason reason) {
        return new ItemValue(false, m, 0, 0, null, null, false, reason);
    }
}
