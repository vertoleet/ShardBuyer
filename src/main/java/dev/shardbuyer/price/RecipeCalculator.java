package dev.shardbuyer.price;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Автоматический расчёт цен по рецептам сервера.
 * <ul>
 *     <li>Упаковка (9 сушёных ламинарий -> блок и обратно) — цена = сумма ингредиентов (множитель packing = 1.0)</li>
 *     <li>Настоящий крафт (поршень) — сумма ингредиентов * craft-multiplier (дороже, чем по отдельности)</li>
 *     <li>Переплавка (ламинария -> сушёная) — цена входа * smelting-multiplier</li>
 *     <li>Камнерез / кузнечный стол — свои множители</li>
 * </ul>
 * Если предмет можно получить несколькими рецептами, берётся самый дешёвый (защита от абуза).
 * Цены, заданные вручную в prices.yml, никогда не перезаписываются.
 */
public final class RecipeCalculator {

    public enum Type { CRAFT, PACKING, SMELTING, STONECUTTING, SMITHING }

    public static final class Settings {
        public final boolean enabled;
        public final double craft;
        public final double packing;
        public final double smelting;
        public final double stonecutting;
        public final double smithing;
        public final int maxIterations;
        public final int roundDecimals;
        public final Set<Material> exclude;
        public final Set<Material> ignoreIngredients;
        public final boolean includePluginRecipes;

        Settings(ConfigurationSection s, Logger log) {
            enabled = s == null || s.getBoolean("enabled", true);
            craft = s == null ? 1.15 : s.getDouble("craft-multiplier", 1.15);
            packing = s == null ? 1.0 : s.getDouble("packing-multiplier", 1.0);
            smelting = s == null ? 1.5 : s.getDouble("smelting-multiplier", 1.5);
            stonecutting = s == null ? 1.0 : s.getDouble("stonecutting-multiplier", 1.0);
            smithing = s == null ? 1.1 : s.getDouble("smithing-multiplier", 1.1);
            maxIterations = s == null ? 60 : Math.max(1, s.getInt("max-iterations", 60));
            roundDecimals = s == null ? 2 : Math.max(0, s.getInt("round-decimals", 2));
            includePluginRecipes = s != null && s.getBoolean("include-plugin-recipes", false);
            exclude = materials(s == null ? List.of() : s.getStringList("exclude"), log);
            ignoreIngredients = materials(s == null ? List.of() : s.getStringList("ignore-ingredients"), log);
        }

        public static Settings from(ConfigurationSection s, Logger log) {
            return new Settings(s, log);
        }
    }

    private static final class Info {
        final Material output;
        final int amount;
        final List<List<Material>> slots;
        Type type;

        Info(Material output, int amount, List<List<Material>> slots, Type type) {
            this.output = output;
            this.amount = amount;
            this.slots = slots;
            this.type = type;
        }

        Material singleIngredient() {
            Material m = null;
            for (List<Material> slot : slots) {
                if (slot.size() != 1) {
                    return null;
                }
                if (m == null) {
                    m = slot.get(0);
                } else if (m != slot.get(0)) {
                    return null;
                }
            }
            return m;
        }
    }

    private final Logger log;
    private final Settings settings;

    public RecipeCalculator(Logger log, Settings settings) {
        this.log = log;
        this.settings = settings;
    }

    public Map<Material, Double> calculate(Map<Material, Double> manual) {
        Map<Material, Double> prices = new EnumMap<>(Material.class);
        for (Map.Entry<Material, Double> e : manual.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                prices.put(e.getKey(), e.getValue());
            }
        }
        List<Info> recipes = collect();
        markPacking(recipes);

        double pow = Math.pow(10, settings.roundDecimals + 2);
        int iterations = 0;
        boolean changed = true;
        while (changed && iterations < settings.maxIterations) {
            changed = false;
            iterations++;
            for (Info r : recipes) {
                if (manual.containsKey(r.output) || settings.exclude.contains(r.output)) {
                    continue;
                }
                double cost = 0;
                boolean ok = !r.slots.isEmpty();
                for (List<Material> slot : r.slots) {
                    double best = Double.MAX_VALUE;
                    for (Material m : slot) {
                        if (settings.ignoreIngredients.contains(m)) {
                            continue;
                        }
                        Double p = prices.get(m);
                        if (p != null && p > 0 && p < best) {
                            best = p;
                        }
                    }
                    if (best == Double.MAX_VALUE) {
                        ok = false;
                        break;
                    }
                    cost += best;
                }
                if (!ok) {
                    continue;
                }
                double mult = switch (r.type) {
                    case CRAFT -> settings.craft;
                    case PACKING -> settings.packing;
                    case SMELTING -> settings.smelting;
                    case STONECUTTING -> settings.stonecutting;
                    case SMITHING -> settings.smithing;
                };
                double price = Math.round(cost * mult / r.amount * pow) / pow;
                if (price <= 0) {
                    continue;
                }
                Double current = prices.get(r.output);
                if (current == null || price < current - 1e-9) {
                    prices.put(r.output, price);
                    changed = true;
                }
            }
        }
        // финальное округление
        double p2 = Math.pow(10, settings.roundDecimals);
        for (Map.Entry<Material, Double> e : prices.entrySet()) {
            if (!manual.containsKey(e.getKey())) {
                double v = Math.round(e.getValue() * p2) / p2;
                e.setValue(v <= 0 ? 1.0 / p2 : v);
            }
        }
        log.info("Auto pricing: " + recipes.size() + " recipes, " + iterations + " iterations, " + prices.size() + " prices");
        return prices;
    }

    private List<Info> collect() {
        List<Info> list = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r;
            try {
                r = it.next();
            } catch (Throwable t) {
                continue;
            }
            try {
                Info info = toInfo(r);
                if (info != null) {
                    list.add(info);
                }
            } catch (Throwable t) {
                // экзотический рецепт другого плагина — пропускаем
            }
        }
        return list;
    }

    private Info toInfo(Recipe r) {
        if (r == null) {
            return null;
        }
        if (!settings.includePluginRecipes && r instanceof org.bukkit.Keyed k
                && !"minecraft".equals(k.getKey().getNamespace())) {
            return null;
        }
        ItemStack result = r.getResult();
        if (result == null || result.getType().isAir() || result.getAmount() <= 0) {
            return null;
        }
        if (result.hasItemMeta()) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null && (meta.hasDisplayName() || meta.hasLore() || meta.hasCustomModelData())) {
                return null; // кастомный предмет
            }
        }
        List<List<Material>> slots = new ArrayList<>();
        Type type;
        if (r instanceof ShapedRecipe sr) {
            Map<Character, RecipeChoice> map = sr.getChoiceMap();
            for (String row : sr.getShape()) {
                for (char c : row.toCharArray()) {
                    RecipeChoice ch = map.get(c);
                    if (ch == null) {
                        continue;
                    }
                    List<Material> mats = materials(ch);
                    if (mats.isEmpty()) {
                        return null;
                    }
                    slots.add(mats);
                }
            }
            type = Type.CRAFT;
        } else if (r instanceof ShapelessRecipe sl) {
            for (RecipeChoice ch : sl.getChoiceList()) {
                if (ch == null) {
                    continue;
                }
                List<Material> mats = materials(ch);
                if (mats.isEmpty()) {
                    return null;
                }
                slots.add(mats);
            }
            type = Type.CRAFT;
        } else if (r instanceof CookingRecipe<?> cr) {
            List<Material> mats = materials(cr.getInputChoice());
            if (mats.isEmpty()) {
                return null;
            }
            slots.add(mats);
            type = Type.SMELTING;
        } else if (r instanceof StonecuttingRecipe st) {
            List<Material> mats = materials(st.getInputChoice());
            if (mats.isEmpty()) {
                return null;
            }
            slots.add(mats);
            type = Type.STONECUTTING;
        } else if (r instanceof SmithingTransformRecipe sm) {
            for (RecipeChoice ch : new RecipeChoice[]{sm.getTemplate(), sm.getBase(), sm.getAddition()}) {
                if (ch == null) {
                    continue;
                }
                List<Material> mats = materials(ch);
                if (mats.isEmpty()) {
                    return null;
                }
                slots.add(mats);
            }
            type = Type.SMITHING;
        } else {
            return null;
        }
        if (slots.isEmpty()) {
            return null;
        }
        // рецепт, где результат входит в ингредиенты (например, окраска) — пропускаем, чтобы не было циклов
        for (List<Material> slot : slots) {
            if (slot.contains(result.getType())) {
                return null;
            }
        }
        return new Info(result.getType(), result.getAmount(), slots, type);
    }

    /** Помечаем пары «упаковка/распаковка»: N×A -> 1×B и 1×B -> N×A. */
    private void markPacking(List<Info> recipes) {
        Set<String> forward = new HashSet<>();
        for (Info r : recipes) {
            if (r.type != Type.CRAFT || r.amount != 1) {
                continue;
            }
            Material m = r.singleIngredient();
            if (m != null && r.slots.size() > 1) {
                forward.add(m.name() + "|" + r.slots.size() + "|" + r.output.name());
            }
        }
        Set<String> confirmed = new HashSet<>();
        for (Info r : recipes) {
            if (r.type != Type.CRAFT || r.slots.size() != 1 || r.amount <= 1) {
                continue;
            }
            Material packed = r.singleIngredient();
            if (packed == null) {
                continue;
            }
            String key = r.output.name() + "|" + r.amount + "|" + packed.name();
            if (forward.contains(key)) {
                r.type = Type.PACKING;
                confirmed.add(key);
            }
        }
        for (Info r : recipes) {
            if (r.type != Type.CRAFT || r.amount != 1) {
                continue;
            }
            Material m = r.singleIngredient();
            if (m != null && confirmed.contains(m.name() + "|" + r.slots.size() + "|" + r.output.name())) {
                r.type = Type.PACKING;
            }
        }
    }

    private static List<Material> materials(RecipeChoice choice) {
        List<Material> out = new ArrayList<>();
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            for (Material m : mc.getChoices()) {
                if (m != null && !m.isAir()) {
                    out.add(m);
                }
            }
        } else if (choice instanceof RecipeChoice.ExactChoice ec) {
            for (ItemStack is : ec.getChoices()) {
                if (is != null && !is.getType().isAir()) {
                    out.add(is.getType());
                }
            }
        }
        return out;
    }

    static Set<Material> materials(List<String> names, Logger log) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String n : names) {
            Material m = Material.matchMaterial(n.trim().toUpperCase(Locale.ROOT));
            if (m != null) {
                set.add(m);
            } else if (log != null) {
                log.warning("Unknown material: " + n);
            }
        }
        return set;
    }
}
