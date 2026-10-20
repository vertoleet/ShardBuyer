package dev.shardbuyer.price;

import dev.shardbuyer.ShardBuyer;
import dev.shardbuyer.config.Settings;
import dev.shardbuyer.util.Keys;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Хранит цены предметов и оценивает конкретные ItemStack. */
public final class PriceManager {

    private final ShardBuyer plugin;
    private volatile Map<Material, Double> manual = Collections.emptyMap();
    private volatile Map<Material, Double> pointOverrides = Collections.emptyMap();
    private volatile Map<Material, Double> prices = Collections.emptyMap();
    private volatile Set<Material> disabled = Collections.emptySet();
    private volatile EnchantPricing enchants;
    private RecipeCalculator.Settings autoCalc;
    private YamlConfiguration pricesCfg;

    public PriceManager(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    public void load() {
        pricesCfg = plugin.files().load("prices.yml", false);
        Map<Material, Double> man = new EnumMap<>(Material.class);
        Map<Material, Double> pts = new EnumMap<>(Material.class);
        ConfigurationSection sec = pricesCfg.getConfigurationSection("prices");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                Material m = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                if (m == null || m.isAir()) {
                    plugin.getLogger().warning("prices.yml: unknown item '" + key + "' (skipped)");
                    continue;
                }
                if (sec.isConfigurationSection(key)) {
                    ConfigurationSection s = sec.getConfigurationSection(key);
                    man.put(m, s.getDouble("price", 0));
                    if (s.contains("points")) {
                        pts.put(m, s.getDouble("points"));
                    }
                } else {
                    man.put(m, sec.getDouble(key, 0));
                }
            }
        }
        Set<Material> dis = EnumSet.noneOf(Material.class);
        dis.addAll(RecipeCalculator.materials(pricesCfg.getStringList("disabled"), plugin.getLogger()));
        autoCalc = RecipeCalculator.Settings.from(pricesCfg.getConfigurationSection("auto-calculation"), plugin.getLogger());
        manual = man;
        pointOverrides = pts;
        disabled = dis;
        Map<Material, Double> initial = new EnumMap<>(Material.class);
        man.forEach((m, p) -> {
            if (p > 0 && !dis.contains(m)) {
                initial.put(m, p);
            }
        });
        prices = Collections.unmodifiableMap(initial);
        enchants = new EnchantPricing(plugin.files().load("enchantments.yml", false));
    }

    /** Пересчёт цен по рецептам. Только из основного потока. */
    public int recalculate() {
        Map<Material, Double> result;
        if (autoCalc.enabled) {
            result = new RecipeCalculator(plugin.getLogger(), autoCalc).calculate(manual);
        } else {
            result = new EnumMap<>(Material.class);
            result.putAll(manual);
        }
        Map<Material, Double> clean = new EnumMap<>(Material.class);
        for (Map.Entry<Material, Double> e : result.entrySet()) {
            Material m = e.getKey();
            if (e.getValue() == null || e.getValue() <= 0 || disabled.contains(m) || !m.isItem()) {
                continue;
            }
            clean.put(m, e.getValue());
        }
        prices = Collections.unmodifiableMap(clean);
        return clean.size();
    }

    public Map<Material, Double> all() {
        return prices;
    }

    public double base(Material m) {
        Double d = prices.get(m);
        return d == null ? 0 : d;
    }

    public boolean hasPrice(Material m) {
        return prices.containsKey(m);
    }

    public boolean isManual(Material m) {
        return manual.containsKey(m);
    }

    public EnchantPricing enchants() {
        return enchants;
    }

    public ItemValue evaluate(ItemStack is) {
        if (is == null || is.getType().isAir() || is.getAmount() <= 0) {
            return ItemValue.EMPTY;
        }
        Settings s = plugin.settings();
        Material m = is.getType();
        if (disabled.contains(m)) {
            return ItemValue.unsellable(m, ItemValue.Reason.DISABLED);
        }
        double enchantValue = 0;
        boolean special = false;
        double durability = 1;
        String marketKey = m.name();
        if (is.hasItemMeta()) {
            ItemMeta meta = is.getItemMeta();
            if (meta != null) {
                if (Keys.isGui(meta)) {
                    return ItemValue.unsellable(m, ItemValue.Reason.GUI);
                }
                if (meta.hasDisplayName()) {
                    if (!s.allowRenamed) {
                        return ItemValue.unsellable(m, ItemValue.Reason.RENAMED);
                    }
                    special = true;
                }
                if (meta.hasLore() && !s.allowLore) {
                    return ItemValue.unsellable(m, ItemValue.Reason.LORE);
                }
                if (meta.hasCustomModelData() && !s.allowCustomModelData) {
                    return ItemValue.unsellable(m, ItemValue.Reason.CUSTOM_MODEL);
                }
                if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState()) {
                    BlockState state = bsm.getBlockState();
                    if (state instanceof Container c && !isEmpty(c.getInventory().getContents())) {
                        return ItemValue.unsellable(m, ItemValue.Reason.CONTAINER_NOT_EMPTY);
                    }
                }
                if (meta instanceof EnchantmentStorageMeta esm && esm.hasStoredEnchants()) {
                    Map<Enchantment, Integer> stored = esm.getStoredEnchants();
                    enchantValue += enchants.book(stored);
                    marketKey = m.name() + ":" + bookKey(stored);
                }
                if (meta.hasEnchants()) {
                    enchantValue += enchants.item(meta.getEnchants());
                    special = true;
                }
                if (s.durabilityEnabled && meta instanceof Damageable d && d.hasDamage() && m.getMaxDurability() > 0) {
                    double max = m.getMaxDurability();
                    durability = Math.max(s.durabilityMinFactor, 1.0 - d.getDamage() / max);
                    special = true;
                }
            }
        }
        Double base = prices.get(m);
        if (base == null) {
            if (enchantValue <= 0) {
                return ItemValue.unsellable(m, ItemValue.Reason.NO_PRICE);
            }
            base = 0.0;
        }
        double unit = base * durability + enchantValue * (s.durabilityAffectsEnchants ? durability : 1);
        if (unit <= 0) {
            return ItemValue.unsellable(m, ItemValue.Reason.NO_PRICE);
        }
        Double ptsOverride = pointOverrides.get(m);
        double points = ptsOverride != null ? ptsOverride : unit * plugin.levels().pointsPerMoney();
        return new ItemValue(true, m, unit, points, marketKey, plugin.categories().categoryOf(m), special, ItemValue.Reason.OK);
    }

    private static String bookKey(Map<Enchantment, Integer> stored) {
        if (stored.size() == 1) {
            Map.Entry<Enchantment, Integer> e = stored.entrySet().iterator().next();
            return e.getKey().getKey().getKey() + "_" + e.getValue();
        }
        TreeMap<String, Integer> sorted = new TreeMap<>();
        stored.forEach((e, l) -> sorted.put(e.getKey().getKey(), l));
        StringBuilder sb = new StringBuilder();
        sorted.forEach((k, l) -> sb.append(sb.length() == 0 ? "" : "+").append(k).append('_').append(l));
        return sb.toString();
    }

    public static boolean isEmpty(ItemStack[] contents) {
        if (contents == null) {
            return true;
        }
        for (ItemStack c : contents) {
            if (c != null && !c.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------- редактирование ----------------------

    public void setPrice(Material m, double price) {
        pricesCfg.set("prices." + m.name(), price);
        plugin.files().saveSync(pricesCfg, "prices.yml");
    }

    public void removePrice(Material m) {
        pricesCfg.set("prices." + m.name(), null);
        plugin.files().saveSync(pricesCfg, "prices.yml");
    }

    /** Выгружает все рассчитанные цены в computed-prices.yml — удобно копировать в prices.yml и править. */
    public int dump() {
        YamlConfiguration out = new YamlConfiguration();
        List<Material> list = new ArrayList<>(prices.keySet());
        list.sort((a, b) -> a.name().compareTo(b.name()));
        for (Material m : list) {
            out.set("prices." + m.name(), prices.get(m));
            if (!manual.containsKey(m)) {
                out.set("auto." + m.name(), true);
            }
        }
        plugin.files().saveSync(out, "computed-prices.yml");
        return list.size();
    }
}
