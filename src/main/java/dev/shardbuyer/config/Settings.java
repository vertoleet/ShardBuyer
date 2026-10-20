package dev.shardbuyer.config;

import dev.shardbuyer.util.SoundSpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Разобранный config.yml. */
public final class Settings {

    public enum MenuMode { CATEGORIES, PAGED }

    public enum ShulkerMode { RETURN_BOX, SELL_BOX }

    public enum Notify { CHAT, ACTIONBAR, NONE }

    public enum ClickAction { SELL_ALL, SELL_STACK, SELL_ONE, TOGGLE_AUTOSELL, NONE }

    public final MenuMode menuMode;
    public final long clickCooldownMs;

    public final boolean allowRenamed;
    public final boolean allowLore;
    public final boolean allowCustomModelData;
    public final boolean protectSpecialItems;
    public final boolean durabilityEnabled;
    public final double durabilityMinFactor;
    public final boolean durabilityAffectsEnchants;
    public final Map<ClickType, ClickAction> clickActions = new EnumMap<>(ClickType.class);
    public final boolean showDetails;
    public final int maxDetailLines;

    public final boolean shulkerSellContents;
    public final ShulkerMode shulkerMode;

    public final boolean sellOnClose;
    public final boolean closeAfterButton;

    public final boolean autosellEnabled;
    public final boolean autosellDefault;
    public final boolean autosellGlow;
    public final Notify autosellNotify;

    public final boolean loreEnabled;
    public final boolean loreDefault;
    public final boolean loreInCreative;
    public final boolean loreInContainers;
    public final List<String> loreLines;
    public final int loreRefreshSeconds;

    public final Map<String, Double> permissionMultipliers = new LinkedHashMap<>();

    public final SoundSpec soundSell;
    public final SoundSpec soundFail;
    public final SoundSpec soundLevelUp;
    public final SoundSpec soundClick;
    public final SoundSpec soundToggle;

    public final int barLength;
    public final String barSymbol;
    public final String barComplete;
    public final String barIncomplete;
    public final String levelUpTitle;
    public final String levelUpSubtitle;

    public final int autosaveMinutes;

    public Settings(YamlConfiguration c, Logger log) {
        menuMode = enumValue(MenuMode.class, c.getString("menu-mode"), MenuMode.CATEGORIES, log, "menu-mode");
        clickCooldownMs = c.getLong("click-cooldown-ms", 120);

        allowRenamed = c.getBoolean("selling.allow-renamed-items", false);
        allowLore = c.getBoolean("selling.allow-items-with-lore", false);
        allowCustomModelData = c.getBoolean("selling.allow-custom-model-data", false);
        protectSpecialItems = c.getBoolean("selling.protect-special-items", true);
        durabilityEnabled = c.getBoolean("selling.durability.enabled", true);
        durabilityMinFactor = c.getDouble("selling.durability.min-factor", 0.1);
        durabilityAffectsEnchants = c.getBoolean("selling.durability.affect-enchantments", false);
        ConfigurationSection ca = c.getConfigurationSection("selling.click-actions");
        if (ca != null && !ca.getKeys(false).isEmpty()) {
            for (String k : ca.getKeys(false)) {
                ClickType type = enumValue(ClickType.class, k, null, log, "selling.click-actions");
                ClickAction act = enumValue(ClickAction.class, ca.getString(k), ClickAction.NONE, log, "selling.click-actions." + k);
                if (type != null) {
                    clickActions.put(type, act);
                }
            }
        } else {
            clickActions.put(ClickType.LEFT, ClickAction.SELL_ALL);
            clickActions.put(ClickType.RIGHT, ClickAction.SELL_STACK);
            clickActions.put(ClickType.SHIFT_LEFT, ClickAction.SELL_ONE);
            clickActions.put(ClickType.SHIFT_RIGHT, ClickAction.TOGGLE_AUTOSELL);
        }
        showDetails = c.getBoolean("selling.show-details", true);
        maxDetailLines = c.getInt("selling.max-detail-lines", 8);

        shulkerSellContents = c.getBoolean("shulker-boxes.sell-contents", true);
        shulkerMode = enumValue(ShulkerMode.class, c.getString("shulker-boxes.mode"), ShulkerMode.RETURN_BOX, log, "shulker-boxes.mode");

        sellOnClose = c.getBoolean("sell-menu.sell-on-close", true);
        closeAfterButton = c.getBoolean("sell-menu.close-after-button", false);

        autosellEnabled = c.getBoolean("autosell.enabled", true);
        autosellDefault = c.getBoolean("autosell.default-enabled", true);
        autosellGlow = c.getBoolean("autosell.glow-in-menu", true);
        autosellNotify = enumValue(Notify.class, c.getString("autosell.notify"), Notify.ACTIONBAR, log, "autosell.notify");

        loreEnabled = c.getBoolean("price-lore.enabled", true);
        loreDefault = c.getBoolean("price-lore.default-enabled", true);
        loreInCreative = c.getBoolean("price-lore.show-in-creative", false);
        loreInContainers = c.getBoolean("price-lore.show-in-containers", true);
        loreLines = new ArrayList<>(c.getStringList("price-lore.lines"));
        loreRefreshSeconds = c.getInt("price-lore.refresh-seconds", 0);

        ConfigurationSection pm = c.getConfigurationSection("multipliers.permissions");
        if (pm != null) {
            for (String k : pm.getKeys(false)) {
                permissionMultipliers.put(k, pm.getDouble(k, 1.0));
            }
        }

        soundSell = SoundSpec.parse(c.getString("sounds.sell"));
        soundFail = SoundSpec.parse(c.getString("sounds.fail"));
        soundLevelUp = SoundSpec.parse(c.getString("sounds.level-up"));
        soundClick = SoundSpec.parse(c.getString("sounds.click"));
        soundToggle = SoundSpec.parse(c.getString("sounds.toggle"));

        barLength = Math.max(1, c.getInt("leveling.progress-bar.length", 20));
        barSymbol = c.getString("leveling.progress-bar.symbol", "|");
        barComplete = c.getString("leveling.progress-bar.complete-color", "&a");
        barIncomplete = c.getString("leveling.progress-bar.incomplete-color", "&8");
        levelUpTitle = c.getString("leveling.level-up.title", "");
        levelUpSubtitle = c.getString("leveling.level-up.subtitle", "");

        autosaveMinutes = Math.max(1, c.getInt("data.autosave-minutes", 5));
    }

    public static <E extends Enum<E>> E enumValue(Class<E> type, String raw, E def, Logger log, String path) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            if (log != null) {
                log.warning("Invalid value '" + raw + "' at " + path + ", using " + def);
            }
            return def;
        }
    }
}
