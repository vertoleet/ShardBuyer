package dev.shardbuyer.config;

import dev.shardbuyer.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * lang/&lt;code&gt;/messages.yml. Supports &amp;-colors, HEX (&amp;#RRGGBB), gradients and {prefix}.
 * An empty string means "do not send".
 * <p>
 * Item names passed as {@link Tr} are sent as translatable components, so every player
 * sees the item name in the language of their own Minecraft client.
 */
public final class Messages {

    /** Translatable name (translation key + fallback text). */
    public static final class Tr {
        public final String key;
        public final String fallback;

        public Tr(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        @Override
        public String toString() {
            return fallback;
        }
    }

    private static final char MARK_START = '';
    private static final char MARK_END = '';

    private final YamlConfiguration cfg;
    private final String prefix;
    private final Map<String, String> globals;
    private boolean adventureBroken;

    public Messages(YamlConfiguration cfg, Map<String, String> globals) {
        this.cfg = cfg;
        this.globals = globals == null ? new HashMap<>() : globals;
        this.prefix = cfg.getString("prefix", "");
    }

    public String raw(String path) {
        if (cfg.isList(path)) {
            return String.join("\n", cfg.getStringList(path));
        }
        return cfg.getString(path, "");
    }

    public List<String> list(String path) {
        if (cfg.isList(path)) {
            return cfg.getStringList(path);
        }
        String s = cfg.getString(path, "");
        return s.isEmpty() ? List.of() : List.of(s.split("\n"));
    }

    public String get(String path, Object... kv) {
        return format(raw(path), kv);
    }

    /** Plain text (translatable names are replaced with their fallback). */
    public String format(String text, Object... kv) {
        return build(text, null, kv);
    }

    private String build(String text, List<Tr> trs, Object... kv) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Map<String, String> map = new HashMap<>(globals);
        map.put("prefix", prefix);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            String value;
            if (v instanceof Tr tr && trs != null) {
                value = "" + MARK_START + trs.size() + MARK_END;
                trs.add(tr);
            } else {
                value = v == null ? "" : String.valueOf(v);
            }
            map.put(String.valueOf(kv[i]), value);
        }
        // два прохода, чтобы {prefix} и {cmd_*} внутри подставленных значений тоже сработали
        return Text.color(Text.replace(Text.replace(text, map), map));
    }

    public void send(CommandSender to, String path, Object... kv) {
        if (to == null) {
            return;
        }
        List<Tr> trs = new ArrayList<>();
        String text = build(raw(path), trs, kv);
        if (text.isEmpty()) {
            return;
        }
        for (String line : text.split("\n")) {
            deliver(to, line, trs, false);
        }
    }

    public void sendRaw(CommandSender to, String message) {
        if (to == null || message == null || message.isEmpty()) {
            return;
        }
        for (String line : message.split("\n")) {
            to.sendMessage(line);
        }
    }

    public void actionBar(Player p, String path, Object... kv) {
        List<Tr> trs = new ArrayList<>();
        String text = build(raw(path), trs, kv);
        if (!text.isEmpty()) {
            deliver(p, text.replace('\n', ' '), trs, true);
        }
    }

    private void deliver(CommandSender to, String line, List<Tr> trs, boolean actionBar) {
        if (trs.isEmpty() || line.indexOf(MARK_START) < 0) {
            sendPlain(to, line, actionBar);
            return;
        }
        if (!adventureBroken) {
            try {
                Component c = LegacyComponentSerializer.legacySection().deserialize(line);
                for (int i = 0; i < trs.size(); i++) {
                    String marker = "" + MARK_START + i + MARK_END;
                    if (line.contains(marker)) {
                        Component name = Component.translatable(trs.get(i).key);
                        c = c.replaceText(b -> b.matchLiteral(marker).replacement(name));
                    }
                }
                if (actionBar && to instanceof Player p) {
                    p.sendActionBar(c);
                } else {
                    to.sendMessage(c);
                }
                return;
            } catch (Throwable t) {
                adventureBroken = true; // очень старое ядро — дальше шлём обычный текст
            }
        }
        String plain = line;
        for (int i = 0; i < trs.size(); i++) {
            plain = plain.replace("" + MARK_START + i + MARK_END, trs.get(i).fallback);
        }
        sendPlain(to, plain, actionBar);
    }

    private static void sendPlain(CommandSender to, String line, boolean actionBar) {
        if (actionBar && to instanceof Player p) {
            p.sendActionBar(line);
        } else {
            to.sendMessage(line);
        }
    }
}
