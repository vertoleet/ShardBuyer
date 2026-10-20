package dev.shardbuyer;

import dev.shardbuyer.booster.BoosterManager;
import dev.shardbuyer.category.CategoryManager;
import dev.shardbuyer.command.AdminCommand;
import dev.shardbuyer.command.SellCommand;
import dev.shardbuyer.command.BuyerCommand;
import dev.shardbuyer.command.CommandRegistry;
import dev.shardbuyer.command.WorthCommand;
import dev.shardbuyer.config.ConfigFiles;
import dev.shardbuyer.config.Messages;
import dev.shardbuyer.config.Settings;
import dev.shardbuyer.data.PlayerData;
import dev.shardbuyer.data.PlayerDataManager;
import dev.shardbuyer.exclusive.ExclusiveManager;
import dev.shardbuyer.gui.MenuManager;
import dev.shardbuyer.hook.EconomyHook;
import dev.shardbuyer.hook.PriceLoreHook;
import dev.shardbuyer.level.LevelManager;
import dev.shardbuyer.listener.BukkitAutoSellListener;
import dev.shardbuyer.listener.ChatInput;
import dev.shardbuyer.listener.PaperAutoSellListener;
import dev.shardbuyer.listener.PlayerListener;
import dev.shardbuyer.market.MarketManager;
import dev.shardbuyer.price.PriceManager;
import dev.shardbuyer.sell.SellService;
import dev.shardbuyer.util.ItemTemplate;
import dev.shardbuyer.util.Keys;
import dev.shardbuyer.util.Names;
import dev.shardbuyer.util.NumberFormatter;
import dev.shardbuyer.util.TimeFormat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShardBuyer extends JavaPlugin {

    private ConfigFiles files;
    private YamlConfiguration config;
    private YamlConfiguration menusConfig;
    private Settings settings;
    private Messages messages;
    private NumberFormatter moneyFormat;
    private NumberFormatter pointsFormat;
    private TimeFormat timeFormat;
    private final Names names = new Names();

    private EconomyHook economy;
    private PriceManager prices;
    private CategoryManager categories;
    private LevelManager levels;
    private MarketManager market;
    private ExclusiveManager exclusive;
    private BoosterManager booster;
    private PlayerDataManager data;
    private SellService sell;
    private MenuManager menus;
    private ChatInput chatInput;
    private PriceLoreHook loreHook;
    private String loreUnavailableReason = "";
    private CommandRegistry commands;
    private String language = "en";
    private Map<String, String> globals = new HashMap<>();

    /** Встроенные языки. */
    public static final List<String> LANGUAGES = List.of("en", "es", "ru", "uk", "pt", "fr");
    private BukkitTask heartbeat;
    private long ticks;

    @Override
    public void onEnable() {
        Keys.init(this);
        ItemTemplate.setLogger(getLogger());
        files = new ConfigFiles(this);
        economy = new EconomyHook(getLogger());
        data = new PlayerDataManager(this);
        market = new MarketManager(this);
        exclusive = new ExclusiveManager(this);
        booster = new BoosterManager(this);
        prices = new PriceManager(this);
        categories = new CategoryManager(this);
        levels = new LevelManager(this);
        sell = new SellService(this);
        menus = new MenuManager(this);
        chatInput = new ChatInput(this);

        loadConfigs();
        prices.recalculate();
        categories.resolve();

        market.load();
        exclusive.loadState();
        booster.load();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(menus, this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(chatInput, this);
        try {
            Class.forName("org.bukkit.event.player.PlayerAttemptPickupItemEvent");
            pm.registerEvents(new PaperAutoSellListener(this), this);
        } catch (Throwable t) {
            pm.registerEvents(new BukkitAutoSellListener(this), this);
        }

        commands = new CommandRegistry(this);
        commands.register("menu", "buyer", "Open the buyer menu", new BuyerCommand(this));
        commands.register("sell", "sell", "Sell items", new SellCommand(this));
        commands.register("worth", "worth", "Item prices", new WorthCommand(this));
        commands.register("admin", "shardbuyer", "ShardBuyer admin commands", new AdminCommand(this));
        commands.syncPlayers();
        getLogger().info("Commands: " + String.join(", ", commands.describe()));

        hookProtocolLib(false);
        if (pm.isPluginEnabled("PlaceholderAPI")) {
            try {
                new dev.shardbuyer.hook.PapiExpansion(this).register();
                getLogger().info("PlaceholderAPI hooked: %shardbuyer_...% placeholders registered.");
            } catch (Throwable t) {
                getLogger().warning("Could not register placeholders: " + t);
            }
        }

        data.loadOnline();
        heartbeat = getServer().getScheduler().runTaskTimer(this, this::heartbeat, 20L, 20L);

        // повторный расчёт через тик — чтобы учесть рецепты, которые другие плагины добавили в onEnable
        getServer().getScheduler().runTask(this, () -> {
            if (loreHook == null) {
                hookProtocolLib(true); // ProtocolLib мог включиться позже нас
            }
            int n = prices.recalculate();
            categories.resolve();
            getLogger().info("Loaded " + n + " prices. Economy: " + economy.name() + ". Language: " + language);
            if (!economy.available()) {
                getLogger().severe("No economy found! Install Vault and an economy plugin (EssentialsX, CMI, ...)");
            }
        });
    }

    @Override
    public void onDisable() {
        if (heartbeat != null) {
            heartbeat.cancel();
        }
        if (menus != null) {
            menus.shutdown();
        }
        if (loreHook != null) {
            try {
                loreHook.unregister();
            } catch (Throwable ignored) {
                // ProtocolLib уже выключен
            }
        }
        if (data != null) {
            data.saveAll(false);
        }
        if (market != null) {
            market.save(false);
        }
        if (exclusive != null) {
            exclusive.saveSync();
        }
        if (commands != null) {
            commands.unregisterAll();
        }
    }

    private void loadConfigs() {
        config = files.load("config.yml", true);
        // распаковываем все встроенные языки, чтобы их было видно в папке lang/
        for (String code : LANGUAGES) {
            for (String f : new String[]{"messages.yml", "menus.yml"}) {
                String path = "lang/" + code + "/" + f;
                if (!files.file(path).exists() && getResource(path) != null) {
                    saveResource(path, false);
                }
            }
        }
        settings = new Settings(config, getLogger());
        language = resolveLanguage(config.getString("language", "en"));
        globals = new HashMap<>();
        globals.put("cmd_menu", commandName("menu", "buyer"));
        globals.put("cmd_sell", commandName("sell", "sell"));
        globals.put("cmd_worth", commandName("worth", "worth"));
        globals.put("cmd_admin", commandName("admin", "shardbuyer"));
        messages = new Messages(files.load("lang/" + language + "/messages.yml", "lang/" + language + "/messages.yml",
                "lang/en/messages.yml"), globals);
        menusConfig = files.load("lang/" + language + "/menus.yml", false);
        moneyFormat = NumberFormatter.from(config.getConfigurationSection("number-format"), 2);
        pointsFormat = NumberFormatter.from(config.getConfigurationSection("points-format"), 0);
        timeFormat = new TimeFormat(config.getConfigurationSection("time-format"));
        names.loadItems(files.load("item-names.yml", false));
        categories.load();
        prices.load();
        levels.load(config);
        market.loadSettings(config);
        exclusive.loadSettings();
    }

    public void reload() {
        menus.closeAll();
        data.saveAll(true);
        market.save(true);
        exclusive.saveAsync();
        loadConfigs();
        prices.recalculate();
        categories.resolve();
        data.refreshAllPermissions();
        hookProtocolLib(true);
        refreshLore();
    }

    /** Язык: встроенный (en, es, ru, uk, pt, fr) или своя папка lang/<код>/ с messages.yml и menus.yml. */
    private String resolveLanguage(String raw) {
        String code = raw == null ? "en" : raw.trim().toLowerCase(Locale.ROOT);
        if (LANGUAGES.contains(code) || files.file("lang/" + code + "/messages.yml").exists()) {
            return code;
        }
        getLogger().warning("Unknown language '" + raw + "', using 'en'. Available: " + LANGUAGES);
        return "en";
    }

    /** Имя команды из config.yml (для подсказок в сообщениях). */
    private String commandName(String key, String def) {
        String n = config.getString("commands." + key + ".name", def);
        n = n == null ? def : n.trim().toLowerCase(Locale.ROOT);
        while (n.startsWith("/")) {
            n = n.substring(1);
        }
        return n.isEmpty() ? def : n;
    }

    /** {cmd_menu}, {cmd_sell}, {cmd_worth}, {cmd_admin}. */
    public Map<String, String> commandPlaceholders() {
        return globals;
    }

    public String language() {
        return language;
    }

    public YamlConfiguration mainConfig() {
        return config;
    }

    /** Перечитать prices.yml и пересчитать (после /shardbuyer setprice). */
    public void reloadPrices() {
        prices.load();
        prices.recalculate();
        categories.resolve();
        refreshLore();
    }

    private void refreshLore() {
        if (loreHook != null) {
            for (Player p : getServer().getOnlinePlayers()) {
                p.updateInventory();
            }
        }
    }

    private void heartbeat() {
        long now = System.currentTimeMillis();
        ticks++;
        market.tick(now);
        exclusive.tick(now);
        booster.tick(now);
        menus.tick();
        if (ticks % 30 == 0) {
            data.refreshAllPermissions();
        }
        if (ticks % (settings.autosaveMinutes * 60L) == 0) {
            data.saveAll(true);
            market.save(true);
        }
        if (loreHook != null && settings.loreRefreshSeconds > 0 && ticks % settings.loreRefreshSeconds == 0) {
            refreshLore();
        }
    }

    // ------------------------------------------------------------------ переключатели игрока

    /** Подключение к ProtocolLib. Причину неудачи запоминаем, чтобы показать игроку/админу. */
    public void hookProtocolLib(boolean retry) {
        if (loreHook != null) {
            loreUnavailableReason = "";
            return;
        }
        org.bukkit.plugin.Plugin pl = getServer().getPluginManager().getPlugin("ProtocolLib");
        if (pl == null) {
            loreUnavailableReason = messages.get("lore.reason-missing");
        } else if (!pl.isEnabled()) {
            loreUnavailableReason = messages.get("lore.reason-disabled", "version", pl.getDescription().getVersion());
        } else {
            try {
                PriceLoreHook hook = new PriceLoreHook(this);
                hook.register();
                loreHook = hook;
                loreUnavailableReason = "";
                getLogger().info("ProtocolLib " + pl.getDescription().getVersion() + " hooked: prices in item descriptions are enabled.");
                return;
            } catch (Throwable t) {
                loreUnavailableReason = messages.get("lore.reason-error", "error", String.valueOf(t));
                getLogger().log(java.util.logging.Level.WARNING, "Could not hook into ProtocolLib", t);
            }
        }
        getLogger().warning((retry ? "[retry] " : "") + "Prices in item descriptions are unavailable: "
                + dev.shardbuyer.util.Text.strip(loreUnavailableReason));
    }

    public String loreUnavailableReason() {
        if (!settings.loreEnabled) {
            return messages.get("lore.reason-config");
        }
        return loreUnavailableReason;
    }

    public boolean loreAvailable() {
        return loreHook != null && settings.loreEnabled;
    }

    /** Можно вызывать из сетевого потока: права берутся из кеша. */
    public boolean loreEnabled(Player p) {
        if (!loreAvailable()) {
            return false;
        }
        PlayerData d = data.peek(p.getUniqueId());
        if (d == null || !d.lorePermission()) {
            return false;
        }
        if (d.loreEnabled() == null) {
            return settings.loreDefault;
        }
        return d.loreEnabled();
    }

    public void toggleLore(Player p) {
        if (!loreAvailable()) {
            messages.send(p, "lore.unavailable", "reason", loreUnavailableReason());
            return;
        }
        if (!p.hasPermission("shardbuyer.lore")) {
            messages.send(p, "no-permission");
            return;
        }
        boolean now = !loreEnabled(p);
        data.get(p).loreEnabled(now);
        p.updateInventory();
        messages.send(p, now ? "lore.enabled" : "lore.disabled");
        settings.soundToggle.play(p);
    }

    public void toggleAutosellMaster(Player p) {
        if (!settings.autosellEnabled) {
            messages.send(p, "autosell.disabled");
            return;
        }
        if (!p.hasPermission("shardbuyer.autosell")) {
            messages.send(p, "no-permission");
            return;
        }
        PlayerData d = data.get(p);
        d.autosellEnabled(!d.autosellEnabled());
        messages.send(p, d.autosellEnabled() ? "autosell.master-enabled" : "autosell.master-disabled",
                "count", d.autosell().size());
        settings.soundToggle.play(p);
    }

    // ------------------------------------------------------------------ форматирование

    public String formatPercent(double v) {
        BigDecimal bd = BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        if (bd.scale() < 0) {
            bd = bd.setScale(0, RoundingMode.HALF_UP);
        }
        return bd.toPlainString();
    }

    public String formatMultiplier(double v) {
        BigDecimal bd = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (bd.scale() < 0) {
            bd = bd.setScale(0, RoundingMode.HALF_UP);
        }
        return "x" + bd.toPlainString();
    }

    // ------------------------------------------------------------------ геттеры

    public ConfigFiles files() {
        return files;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public YamlConfiguration menusConfig() {
        return menusConfig;
    }

    public NumberFormatter moneyFormat() {
        return moneyFormat;
    }

    public NumberFormatter pointsFormat() {
        return pointsFormat;
    }

    public TimeFormat timeFormat() {
        return timeFormat;
    }

    public Names names() {
        return names;
    }

    public EconomyHook economy() {
        return economy;
    }

    public PriceManager prices() {
        return prices;
    }

    public CategoryManager categories() {
        return categories;
    }

    public LevelManager levels() {
        return levels;
    }

    public MarketManager market() {
        return market;
    }

    public ExclusiveManager exclusive() {
        return exclusive;
    }

    public BoosterManager booster() {
        return booster;
    }

    public PlayerDataManager data() {
        return data;
    }

    public SellService sell() {
        return sell;
    }

    public MenuManager menus() {
        return menus;
    }

    public ChatInput chatInput() {
        return chatInput;
    }

    public PriceLoreHook loreHook() {
        return loreHook;
    }
}
