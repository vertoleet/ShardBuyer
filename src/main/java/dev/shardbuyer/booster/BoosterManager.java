package dev.shardbuyer.booster;

import dev.shardbuyer.ShardBuyer;
import org.bukkit.configuration.file.YamlConfiguration;

/** Глобальный бустер цен (/shardbuyer booster 1.5 1h). */
public final class BoosterManager {

    private static final String FILE = "data/booster.yml";

    private final ShardBuyer plugin;
    private volatile double multiplier = 1.0;
    private volatile long until;

    public BoosterManager(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    public double multiplier() {
        return active() ? multiplier : 1.0;
    }

    public boolean active() {
        return until > System.currentTimeMillis() && multiplier != 1.0;
    }

    public long timeLeft() {
        return Math.max(0, until - System.currentTimeMillis());
    }

    public void start(double mult, long durationMs) {
        multiplier = mult;
        until = System.currentTimeMillis() + durationMs;
        save();
    }

    public void stop() {
        multiplier = 1.0;
        until = 0;
        save();
    }

    public void tick(long now) {
        if (until != 0 && until <= now) {
            until = 0;
            multiplier = 1.0;
            save();
            plugin.messages().sendRaw(plugin.getServer().getConsoleSender(), plugin.messages().get("booster.ended"));
            for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                plugin.messages().send(p, "booster.ended");
            }
        }
    }

    public void load() {
        YamlConfiguration y = plugin.files().loadData(FILE);
        multiplier = y.getDouble("multiplier", 1.0);
        until = y.getLong("until", 0);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("multiplier", multiplier);
        y.set("until", until);
        plugin.files().saveAsync(y, FILE);
    }
}
