package dev.shardbuyer.data;

import dev.shardbuyer.ShardBuyer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Хранение данных игроков в plugins/ShardBuyer/data/players/&lt;uuid&gt;.yml */
public final class PlayerDataManager {

    private final ShardBuyer plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    private String path(UUID uuid) {
        return "data/players/" + uuid + ".yml";
    }

    /** Данные онлайн-игрока (загружаются при необходимости). */
    public PlayerData get(Player p) {
        PlayerData d = cache.get(p.getUniqueId());
        if (d == null) {
            d = load(p.getUniqueId(), p.getName());
            cache.put(p.getUniqueId(), d);
            refreshPermissions(p, d);
        }
        return d;
    }

    /** Без загрузки с диска — для асинхронных потоков (может вернуть null). */
    public PlayerData peek(UUID uuid) {
        return cache.get(uuid);
    }

    /** Для оффлайн-игроков (команды админа). Возвращает данные и кладёт их в кеш до сохранения. */
    public PlayerData getOffline(OfflinePlayer op) {
        PlayerData d = cache.get(op.getUniqueId());
        if (d != null) {
            return d;
        }
        d = load(op.getUniqueId(), op.getName());
        cache.put(op.getUniqueId(), d);
        return d;
    }

    private PlayerData load(UUID uuid, String name) {
        YamlConfiguration y = plugin.files().loadData(path(uuid));
        PlayerData d = new PlayerData(uuid, name != null ? name : y.getString("name", "?"),
                plugin.settings().autosellDefault);
        ConfigurationSection pts = y.getConfigurationSection("points");
        if (pts != null) {
            for (String k : pts.getKeys(false)) {
                d.allPoints().put(k, pts.getDouble(k));
            }
        }
        if (y.contains("autosell-enabled")) {
            d.autosellEnabled(y.getBoolean("autosell-enabled"));
        }
        for (String s : y.getStringList("autosell")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                d.autosell().add(m);
            }
        }
        if (y.contains("price-lore")) {
            d.loreEnabled(y.getBoolean("price-lore"));
        }
        d.loadStats(y.getDouble("stats.earned", 0), y.getLong("stats.sold", 0));
        d.dirty(false);
        return d;
    }

    private YamlConfiguration serialize(PlayerData d) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", d.name());
        for (Map.Entry<String, Double> e : d.allPoints().entrySet()) {
            y.set("points." + e.getKey(), e.getValue());
        }
        y.set("autosell-enabled", d.autosellEnabled());
        List<String> auto = new ArrayList<>();
        for (Material m : d.autosell()) {
            auto.add(m.name());
        }
        y.set("autosell", auto);
        if (d.loreEnabled() != null) {
            y.set("price-lore", d.loreEnabled());
        }
        y.set("stats.earned", d.earned());
        y.set("stats.sold", d.sold());
        return y;
    }

    public void save(PlayerData d, boolean async) {
        d.dirty(false);
        YamlConfiguration y = serialize(d);
        if (async) {
            plugin.files().saveAsync(y, path(d.uuid()));
        } else {
            plugin.files().saveSync(y, path(d.uuid()));
        }
    }

    public void unload(UUID uuid) {
        PlayerData d = cache.remove(uuid);
        if (d != null && d.dirty()) {
            save(d, true);
        }
    }

    public void saveAll(boolean async) {
        for (PlayerData d : cache.values()) {
            if (d.dirty()) {
                save(d, async);
            }
        }
        // выгружаем оффлайн-игроков, которых загружали админ-командами
        cache.entrySet().removeIf(e -> Bukkit.getPlayer(e.getKey()) == null && !e.getValue().dirty());
    }

    public void loadOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            get(p);
        }
    }

    /** Кешируем множитель из прав (чтобы не дёргать права из сетевого потока). */
    public void refreshPermissions(Player p, PlayerData d) {
        double best = 1.0;
        for (Map.Entry<String, Double> e : plugin.settings().permissionMultipliers.entrySet()) {
            if (e.getValue() > best && p.hasPermission(e.getKey())) {
                best = e.getValue();
            }
        }
        d.permMultiplier(best);
        d.lorePermission(p.hasPermission("shardbuyer.lore"));
    }

    public void refreshAllPermissions() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = cache.get(p.getUniqueId());
            if (d != null) {
                refreshPermissions(p, d);
            }
        }
    }

    public boolean exists(UUID uuid) {
        return new File(plugin.getDataFolder(), path(uuid)).exists();
    }
}
