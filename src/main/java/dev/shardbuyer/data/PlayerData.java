package dev.shardbuyer.data;

import org.bukkit.Material;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Данные игрока: очки прокачки, автопродажа, настройка цены в описании, статистика. */
public final class PlayerData {

    private final UUID uuid;
    private volatile String name;
    private final Map<String, Double> points = new ConcurrentHashMap<>();
    private final Set<Material> autosell = ConcurrentHashMap.newKeySet();
    private volatile boolean autosellEnabled;
    private volatile Boolean loreEnabled;
    private volatile double earned;
    private volatile long sold;
    private volatile double permMultiplier = 1.0;
    private volatile boolean lorePermission = true;
    private volatile boolean dirty;

    public PlayerData(UUID uuid, String name, boolean autosellDefault) {
        this.uuid = uuid;
        this.name = name;
        this.autosellEnabled = autosellDefault;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String n) {
        this.name = n;
    }

    public double points(String key) {
        Double d = points.get(key);
        return d == null ? 0 : d;
    }

    public void setPoints(String key, double value) {
        points.put(key, Math.max(0, value));
        dirty = true;
    }

    public void addPoints(String key, double value) {
        points.merge(key, value, Double::sum);
        if (points.get(key) < 0) {
            points.put(key, 0.0);
        }
        dirty = true;
    }

    public Map<String, Double> allPoints() {
        return points;
    }

    public void clearPoints() {
        points.clear();
        dirty = true;
    }

    public Set<Material> autosell() {
        return autosell;
    }

    public boolean toggleAutosell(Material m) {
        dirty = true;
        if (autosell.remove(m)) {
            return false;
        }
        autosell.add(m);
        return true;
    }

    public boolean autosellEnabled() {
        return autosellEnabled;
    }

    public void autosellEnabled(boolean v) {
        autosellEnabled = v;
        dirty = true;
    }

    public Boolean loreEnabled() {
        return loreEnabled;
    }

    public void loreEnabled(Boolean v) {
        loreEnabled = v;
        dirty = true;
    }

    public double earned() {
        return earned;
    }

    public long sold() {
        return sold;
    }

    public void addStats(double money, long amount) {
        earned += money;
        sold += amount;
        dirty = true;
    }

    public void loadStats(double e, long s) {
        earned = e;
        sold = s;
    }

    public double permMultiplier() {
        return permMultiplier;
    }

    public void permMultiplier(double m) {
        permMultiplier = m;
    }

    public boolean lorePermission() {
        return lorePermission;
    }

    public void lorePermission(boolean v) {
        lorePermission = v;
    }

    public boolean dirty() {
        return dirty;
    }

    public void dirty(boolean d) {
        dirty = d;
    }
}
