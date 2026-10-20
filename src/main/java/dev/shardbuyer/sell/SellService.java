package dev.shardbuyer.sell;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.config.Settings;
import dev.shardbuyer.data.PlayerData;
import dev.shardbuyer.exclusive.ExclusiveManager;
import dev.shardbuyer.level.LevelManager;
import dev.shardbuyer.price.ItemValue;
import dev.shardbuyer.price.PriceManager;
import dev.shardbuyer.util.InventoryUtil;
import dev.shardbuyer.util.Keys;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Вся логика продажи: оценка, расчёт с бонусами/рынком, выплата, очки, сообщения. */
public final class SellService {

    public enum Source { MENU, SELL_MENU, COMMAND, AUTOSELL, EXCLUSIVE }

    public static final class Line {
        public final String key;
        public final Material material;
        public final String category;
        public long amount;
        public double base;
        public double pointsBase;
        public double money;
        public double points;

        Line(String key, Material material, String category) {
            this.key = key;
            this.material = material;
            this.category = category;
        }
    }

    public static final class Quote {
        public final Map<String, Line> lines = new LinkedHashMap<>();
        public final List<ItemStack> leftovers = new ArrayList<>();
        public double money;
        public double points;
        public long amount;
    }

    private final ShardBuyer plugin;

    public SellService(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ расчёты

    /** Итоговый множитель цены игрока для категории: уровень * права * глобальный бустер. */
    public double multiplier(PlayerData d, String category) {
        double m = plugin.levels().multiplier(d, category);
        if (d != null) {
            m *= d.permMultiplier();
        }
        return m * plugin.booster().multiplier();
    }

    /** Цена 1 шт. для игрока прямо сейчас (с рынком и бонусами). Безопасно вызывать из любого потока. */
    public double unitPrice(UUID player, ItemValue v) {
        if (v == null || !v.sellable) {
            return 0;
        }
        PlayerData d = player == null ? null : plugin.data().peek(player);
        return v.unitBase * plugin.market().factor(v.marketKey) * multiplier(d, v.category);
    }

    /** Цена всего стака (для шалкера — стоимость содержимого). Безопасно вызывать из любого потока. */
    public double displayValue(UUID player, ItemStack is) {
        if (is == null || is.getType().isAir()) {
            return 0;
        }
        Settings s = plugin.settings();
        if (s.shulkerSellContents && isShulkerWithContents(is)) {
            double total = 0;
            ShulkerBox box = shulker(is);
            if (box == null) {
                return 0;
            }
            boolean allSold = true;
            for (ItemStack c : box.getInventory().getContents()) {
                if (c == null || c.getType().isAir()) {
                    continue;
                }
                ItemValue v = plugin.prices().evaluate(c);
                if (v.sellable) {
                    total += unitPrice(player, v) * c.getAmount();
                } else {
                    allSold = false;
                }
            }
            if (s.shulkerMode == Settings.ShulkerMode.SELL_BOX && allSold) {
                ItemStack empty = new ItemStack(is.getType());
                total += unitPrice(player, plugin.prices().evaluate(empty));
            }
            return total * is.getAmount();
        }
        ItemValue v = plugin.prices().evaluate(is);
        return v.sellable ? unitPrice(player, v) * is.getAmount() : 0;
    }

    /** Можно ли продавать предмет «быстро» (иконки в меню, /sell all, автопродажа). */
    public boolean quickSellable(ItemStack is) {
        ItemValue v = plugin.prices().evaluate(is);
        return v.sellable && (!plugin.settings().protectSpecialItems || !v.special);
    }

    public Quote quote(Player p, Collection<ItemStack> items) {
        Quote q = new Quote();
        for (ItemStack is : items) {
            collect(is, q);
        }
        finish(p, q);
        return q;
    }

    private void collect(ItemStack original, Quote q) {
        if (original == null || original.getType().isAir() || original.getAmount() <= 0) {
            return;
        }
        Settings s = plugin.settings();
        if (s.shulkerSellContents && isShulkerWithContents(original)) {
            collectShulker(original, q);
            return;
        }
        ItemValue v = plugin.prices().evaluate(original);
        if (!v.sellable) {
            q.leftovers.add(original.clone());
            return;
        }
        add(q, v, original.getAmount());
    }

    private void collectShulker(ItemStack original, Quote q) {
        Settings s = plugin.settings();
        // шалкеры не стакаются, но на всякий случай обрабатываем каждый отдельно
        for (int n = 0; n < original.getAmount(); n++) {
            ItemStack box = original.clone();
            box.setAmount(1);
            BlockStateMeta bsm = (BlockStateMeta) box.getItemMeta();
            ShulkerBox state = (ShulkerBox) bsm.getBlockState();
            Inventory inv = state.getInventory();
            ItemStack[] contents = inv.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack c = contents[i];
                if (c == null || c.getType().isAir()) {
                    continue;
                }
                ItemValue v = plugin.prices().evaluate(c);
                if (v.sellable) {
                    add(q, v, c.getAmount());
                    contents[i] = null;
                }
            }
            inv.setContents(contents);
            bsm.setBlockState(state);
            box.setItemMeta(bsm);
            boolean empty = PriceManager.isEmpty(contents);
            if (empty && s.shulkerMode == Settings.ShulkerMode.SELL_BOX) {
                ItemValue boxValue = plugin.prices().evaluate(box);
                if (boxValue.sellable) {
                    add(q, boxValue, 1);
                    continue;
                }
            }
            q.leftovers.add(box);
        }
    }

    private void add(Quote q, ItemValue v, int amount) {
        Line l = q.lines.computeIfAbsent(v.marketKey, k -> new Line(k, v.material, v.category));
        l.amount += amount;
        l.base += v.unitBase * amount;
        l.pointsBase += v.unitPoints * amount;
    }

    private void finish(Player p, Quote q) {
        PlayerData d = plugin.data().get(p);
        LevelManager lm = plugin.levels();
        boolean affectPoints = plugin.market().affectsPoints();
        double totalMoney = 0;
        double totalPoints = 0;
        long totalAmount = 0;
        for (Line l : q.lines.values()) {
            double f = plugin.market().factorFor(l.key, l.amount);
            l.money = round(l.base * f * multiplier(d, l.category));
            l.points = lm.key(l.category) == null ? 0 : l.pointsBase * (affectPoints ? f : 1.0);
            totalMoney += l.money;
            totalPoints += l.points;
            totalAmount += l.amount;
        }
        q.money = round(totalMoney);
        q.points = totalPoints;
        q.amount = totalAmount;
    }

    // ------------------------------------------------------------------ продажа

    /**
     * Продаёт предметы (они уже должны быть изъяты у игрока).
     * Непродаваемое возвращается игроку. При ошибке экономики — возвращается всё.
     * @return квота или null, если ничего не продано
     */
    public Quote sell(Player p, List<ItemStack> items, Source source) {
        Quote q = quote(p, items);
        if (q.amount <= 0) {
            InventoryUtil.give(p, items);
            if (source != Source.AUTOSELL) {
                plugin.messages().send(p, "sell.nothing");
                plugin.settings().soundFail.play(p);
            }
            return null;
        }
        if (!plugin.economy().deposit(p, q.money)) {
            InventoryUtil.give(p, items);
            plugin.messages().send(p, "sell.failed");
            plugin.settings().soundFail.play(p);
            return null;
        }
        applyRewards(p, q);
        if (!q.leftovers.isEmpty()) {
            int count = 0;
            for (ItemStack is : q.leftovers) {
                count += is.getAmount();
            }
            InventoryUtil.give(p, q.leftovers);
            if (source != Source.AUTOSELL) {
                plugin.messages().send(p, "sell.returned", "count", count);
            }
        }
        notifySale(p, q, source);
        return q;
    }

    /** Продажа из инвентаря игрока по фильтру (max <= 0 — без ограничения). */
    public Quote sellFromInventory(Player p, Predicate<ItemStack> filter, int max, Source source, String nothingPath, Object... kv) {
        List<ItemStack> taken = InventoryUtil.take(p, filter, max);
        if (taken.isEmpty()) {
            plugin.messages().send(p, nothingPath, kv);
            plugin.settings().soundFail.play(p);
            return null;
        }
        return sell(p, taken, source);
    }

    /** Продать всё из инвентаря (кроме защищённых предметов). */
    public Quote sellInventory(Player p) {
        return sellFromInventory(p, this::quickSellable, 0, Source.COMMAND, "sell.nothing");
    }

    private void applyRewards(Player p, Quote q) {
        PlayerData d = plugin.data().get(p);
        LevelManager lm = plugin.levels();
        Map<String, Integer> before = new HashMap<>();
        for (Line l : q.lines.values()) {
            plugin.market().record(l.key, l.amount);
            String key = lm.key(l.category);
            if (key != null && l.points > 0) {
                before.computeIfAbsent(key, k -> lm.infoByKey(d, k).level);
                d.addPoints(key, l.points);
            }
        }
        d.addStats(q.money, q.amount);
        checkLevelUps(p, d, before);
    }

    /** Начисление очков вне обычной продажи (эксклюзивы). */
    public void addPoints(Player p, String category, double points) {
        if (points <= 0) {
            return;
        }
        PlayerData d = plugin.data().get(p);
        String key = plugin.levels().key(category);
        if (key == null) {
            return;
        }
        Map<String, Integer> before = new HashMap<>();
        before.put(key, plugin.levels().infoByKey(d, key).level);
        d.addPoints(key, points);
        checkLevelUps(p, d, before);
    }

    private void checkLevelUps(Player p, PlayerData d, Map<String, Integer> before) {
        boolean any = false;
        for (Map.Entry<String, Integer> e : before.entrySet()) {
            LevelManager.Info info = plugin.levels().infoByKey(d, e.getKey());
            if (info.level > e.getValue()) {
                any = true;
                String catName = LevelManager.GLOBAL_KEY.equals(e.getKey()) ? "" : plugin.categories().displayName(e.getKey());
                String catPart = catName.isEmpty() ? "" : plugin.messages().get("level.category-part", "category", catName);
                plugin.messages().send(p, "level.up", "level", info.level, "bonus", plugin.formatPercent(info.bonus),
                        "category", catPart, "category_name", catName);
                Settings s = plugin.settings();
                String title = plugin.messages().format(s.levelUpTitle, "level", info.level, "bonus", plugin.formatPercent(info.bonus), "category", catName);
                String sub = plugin.messages().format(s.levelUpSubtitle, "level", info.level, "bonus", plugin.formatPercent(info.bonus), "category", catName);
                if (!title.isEmpty() || !sub.isEmpty()) {
                    p.sendTitle(title, sub, 10, 50, 15);
                }
                s.soundLevelUp.play(p);
            }
        }
        if (any && plugin.loreHook() != null) {
            p.updateInventory();
        }
    }

    private void notifySale(Player p, Quote q, Source source) {
        Settings s = plugin.settings();
        String pointsPart = q.points > 0.0001
                ? plugin.messages().get("sell.points-part", "points", plugin.pointsFormat().format(q.points)) : "";
        if (source == Source.AUTOSELL) {
            Line first = q.lines.values().iterator().next();
            Object[] kv = {"money", plugin.moneyFormat().format(q.money), "amount", q.amount,
                    "item", plugin.names().itemArg(first.material), "points", pointsPart};
            switch (s.autosellNotify) {
                case CHAT -> plugin.messages().send(p, "autosell.sold", kv);
                case ACTIONBAR -> plugin.messages().actionBar(p, "autosell.sold", kv);
                default -> {
                }
            }
            return;
        }
        plugin.messages().send(p, "sell.success", "money", plugin.moneyFormat().format(q.money),
                "amount", q.amount, "points", pointsPart, "positions", q.lines.size());
        if (s.showDetails && q.lines.size() > 1) {
            List<Line> lines = new ArrayList<>(q.lines.values());
            lines.sort((a, b) -> Double.compare(b.money, a.money));
            int shown = 0;
            for (Line l : lines) {
                if (shown >= s.maxDetailLines) {
                    plugin.messages().send(p, "sell.detail-more", "count", lines.size() - shown);
                    break;
                }
                plugin.messages().send(p, "sell.detail-line", "item", plugin.names().itemArg(l.material),
                        "amount", l.amount, "money", plugin.moneyFormat().format(l.money));
                shown++;
            }
        }
        s.soundSell.play(p);
    }

    // ------------------------------------------------------------------ эксклюзивы

    public void sellExclusive(Player p, ExclusiveManager.Item item, int max) {
        ExclusiveManager ex = plugin.exclusive();
        int remaining = ex.remaining(p.getUniqueId(), item);
        Object itemName = plugin.names().itemArg(item.material);
        if (remaining <= 0) {
            plugin.messages().send(p, "exclusive.limit-reached", "item", itemName,
                    "time", plugin.timeFormat().format(ex.timeLeft()));
            plugin.settings().soundFail.play(p);
            return;
        }
        int want = max <= 0 ? remaining : Math.min(max, remaining);
        List<ItemStack> taken = InventoryUtil.take(p, is -> is.getType() == item.material && plainForExclusive(is), want);
        int amount = 0;
        for (ItemStack is : taken) {
            amount += is.getAmount();
        }
        if (amount <= 0) {
            plugin.messages().send(p, "sell.nothing-item", "item", itemName);
            plugin.settings().soundFail.play(p);
            return;
        }
        PlayerData d = plugin.data().get(p);
        String category = plugin.categories().categoryOf(item.material);
        double mult = ex.applyLevelBoost() ? multiplier(d, category)
                : d.permMultiplier() * plugin.booster().multiplier();
        double money = round(item.price * amount * mult);
        if (!plugin.economy().deposit(p, money)) {
            InventoryUtil.give(p, taken);
            plugin.messages().send(p, "sell.failed");
            plugin.settings().soundFail.play(p);
            return;
        }
        ex.addUsed(p.getUniqueId(), item, amount);
        double points = ex.givePoints() ? item.points * amount : 0;
        if (points > 0 && plugin.levels().key(category) != null) {
            addPoints(p, category, points);
        } else {
            points = 0;
        }
        d.addStats(money, amount);
        String pointsPart = points > 0 ? plugin.messages().get("sell.points-part", "points", plugin.pointsFormat().format(points)) : "";
        plugin.messages().send(p, "exclusive.sold", "item", itemName, "amount", amount,
                "money", plugin.moneyFormat().format(money), "points", pointsPart,
                "left", ex.remaining(p.getUniqueId(), item), "limit", item.limit);
        plugin.settings().soundSell.play(p);
    }

    private boolean plainForExclusive(ItemStack is) {
        if (!is.hasItemMeta()) {
            return true;
        }
        ItemMeta m = is.getItemMeta();
        if (m == null) {
            return true;
        }
        if (m.hasDisplayName() || m.hasLore() || m.hasEnchants() || m.hasCustomModelData() || Keys.isGui(m)) {
            return false;
        }
        return !(m instanceof Damageable dmg) || !dmg.hasDamage();
    }

    // ------------------------------------------------------------------ утилиты

    public static boolean isShulkerWithContents(ItemStack is) {
        ShulkerBox box = shulker(is);
        return box != null && !PriceManager.isEmpty(box.getInventory().getContents());
    }

    public static ShulkerBox shulker(ItemStack is) {
        if (is == null || !is.hasItemMeta() || !is.getType().name().endsWith("SHULKER_BOX")) {
            return null;
        }
        ItemMeta meta = is.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm) || !bsm.hasBlockState()) {
            return null;
        }
        BlockState st = bsm.getBlockState();
        return st instanceof ShulkerBox sb ? sb : null;
    }

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
