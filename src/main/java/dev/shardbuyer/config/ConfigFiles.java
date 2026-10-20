package dev.shardbuyer.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/** Загрузка/сохранение yml-файлов плагина. */
public final class ConfigFiles {

    private final JavaPlugin plugin;

    public ConfigFiles(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public File file(String name) {
        return new File(plugin.getDataFolder(), name);
    }

    /**
     * Загружает файл из папки плагина (создаёт из jar, если нет).
     * @param withDefaults если true — отсутствующие ключи берутся из версии в jar
     */
    public YamlConfiguration load(String name, boolean withDefaults) {
        File f = file(name);
        if (!f.exists() && plugin.getResource(name) != null) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        if (withDefaults) {
            YamlConfiguration def = loadResource(name);
            if (def != null) {
                cfg.setDefaults(def);
            }
        }
        return cfg;
    }

    /**
     * Loads a file with defaults taken from the jar resource (or a fallback resource,
     * e.g. English messages for a custom language that has no built-in version).
     */
    public YamlConfiguration load(String name, String resource, String fallbackResource) {
        File f = file(name);
        if (!f.exists() && plugin.getResource(resource) != null) {
            plugin.saveResource(resource, false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        YamlConfiguration def = loadResource(resource);
        if (def == null && fallbackResource != null) {
            def = loadResource(fallbackResource);
        }
        if (def != null) {
            cfg.setDefaults(def);
        }
        return cfg;
    }

    public YamlConfiguration loadResource(String name) {
        InputStream in = plugin.getResource(name);
        if (in == null) {
            return null;
        }
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        } catch (IOException e) {
            return null;
        }
    }

    public YamlConfiguration loadData(String name) {
        File f = file(name);
        if (!f.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    /** Сериализует в строку сейчас (в основном потоке), а пишет на диск асинхронно. */
    public void saveAsync(YamlConfiguration cfg, String name) {
        String data = cfg.saveToString();
        File f = file(name);
        if (!plugin.isEnabled()) {
            write(f, data);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(f, data));
    }

    public void saveSync(YamlConfiguration cfg, String name) {
        write(file(name), cfg.saveToString());
    }

    public synchronized void write(File f, String data) {
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tmp = new File(f.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + f.getName(), e);
        }
    }
}
