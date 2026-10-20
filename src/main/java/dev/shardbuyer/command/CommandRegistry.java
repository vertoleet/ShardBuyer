package dev.shardbuyer.command;

import dev.shardbuyer.ShardBuyer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registers commands at runtime so their names and aliases can be set in config.yml (commands: ...).
 * Changing names/aliases requires a server restart.
 */
public final class CommandRegistry {

    private static final class DynamicCommand extends Command implements PluginIdentifiableCommand {
        private final Plugin plugin;
        private final CommandExecutor executor;
        private final TabCompleter completer;

        DynamicCommand(Plugin plugin, String name, String description, List<String> aliases,
                       CommandExecutor executor, TabCompleter completer) {
            super(name, description, "/" + name, aliases);
            this.plugin = plugin;
            this.executor = executor;
            this.completer = completer;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!plugin.isEnabled()) {
                return false;
            }
            return executor.onCommand(sender, this, label, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            List<String> out = completer.onTabComplete(sender, this, alias, args);
            return out == null ? new ArrayList<>() : out;
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }
    }

    private final ShardBuyer plugin;
    private final List<Command> registered = new ArrayList<>();

    public CommandRegistry(ShardBuyer plugin) {
        this.plugin = plugin;
    }

    /**
     * @param key     section in config.yml -> commands
     * @param defName default command name
     */
    public void register(String key, String defName, String description, Object handler) {
        ConfigurationSection s = plugin.mainConfig().getConfigurationSection("commands." + key);
        if (s != null && !s.getBoolean("enabled", true)) {
            return;
        }
        String name = clean(s == null ? defName : s.getString("name", defName));
        if (name.isEmpty()) {
            name = defName;
        }
        List<String> aliases = new ArrayList<>();
        if (s != null) {
            for (String a : s.getStringList("aliases")) {
                String c = clean(a);
                if (!c.isEmpty() && !c.equals(name) && !aliases.contains(c)) {
                    aliases.add(c);
                }
            }
        }
        DynamicCommand cmd = new DynamicCommand(plugin, name, description, aliases,
                (CommandExecutor) handler, (TabCompleter) handler);
        CommandMap map = Bukkit.getCommandMap();
        map.register(plugin.getName().toLowerCase(Locale.ROOT), cmd);
        registered.add(cmd);
        // Если имя уже занято другим плагином (например, /sell и /worth у EssentialsX) — забираем его себе
        boolean override = plugin.mainConfig().getBoolean("commands.override-other-plugins", true);
        if (override) {
            Map<String, Command> known = map.getKnownCommands();
            List<String> labels = new ArrayList<>(aliases);
            labels.add(0, name);
            for (String label : labels) {
                Command existing = known.get(label);
                if (existing != null && existing != cmd) {
                    known.put(label, cmd);
                    plugin.getLogger().info("Command /" + label + " was taken over from another plugin"
                            + " (set commands.override-other-plugins: false to disable).");
                }
            }
        }
    }

    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s.replace(" ", "");
    }

    /** Lets online clients see the new commands in tab-completion (useful after a plugin reload). */
    public void syncPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.updateCommands();
            } catch (Throwable ignored) {
                // old server
            }
        }
    }

    public void unregisterAll() {
        if (registered.isEmpty()) {
            return;
        }
        try {
            CommandMap map = Bukkit.getCommandMap();
            map.getKnownCommands().values().removeIf(registered::contains);
            for (Command c : registered) {
                c.unregister(map);
            }
        } catch (Throwable ignored) {
            // server is shutting down
        }
        registered.clear();
    }

    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Command c : registered) {
            out.add("/" + c.getName() + (c.getAliases().isEmpty() ? "" : " " + c.getAliases()));
        }
        return out;
    }
}
