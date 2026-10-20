package dev.shardbuyer.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Набор плейсхолдеров {key} и условий для строк лора.
 * <p>
 * Строки лора могут начинаться с условий: "[market]&7Рынок: ..." — строка покажется,
 * только если флаг market = true. "[!market]..." — только если false.
 * Несколько условий подряд: "[leveling][!maxed]...".
 * Если строка в шаблоне не пустая, а после подстановки стала пустой — она удаляется.
 * Значение плейсхолдера может содержать \n — тогда строка разобьётся на несколько.
 */
public final class Placeholders {

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Boolean> flags = new HashMap<>();

    public Placeholders put(String key, Object value) {
        values.put(key, value == null ? "" : String.valueOf(value));
        return this;
    }

    public Placeholders flag(String key, boolean value) {
        flags.put(key, value);
        return this;
    }

    public Placeholders putAll(Placeholders other) {
        if (other != null) {
            values.putAll(other.values);
            flags.putAll(other.flags);
        }
        return this;
    }

    public Placeholders copy() {
        return new Placeholders().putAll(this);
    }

    public boolean flag(String key) {
        return Boolean.TRUE.equals(flags.get(key));
    }

    public boolean hasFlag(String key) {
        return flags.containsKey(key);
    }

    public Map<String, String> values() {
        return Collections.unmodifiableMap(values);
    }

    /** Проверка условия вида "flag" или "!flag". Неизвестный флаг = false. */
    public boolean test(String condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        boolean negate = condition.startsWith("!");
        String key = negate ? condition.substring(1) : condition;
        boolean v = flag(key);
        return negate != v;
    }

    public String apply(String s) {
        return Text.color(Text.replace(s, values));
    }

    public String applyRaw(String s) {
        return Text.replace(s, values);
    }

    public List<String> lore(List<String> template) {
        List<String> out = new ArrayList<>();
        if (template == null) {
            return out;
        }
        for (String line : template) {
            String l = line == null ? "" : line;
            boolean show = true;
            while (l.startsWith("[")) {
                int end = l.indexOf(']');
                if (end < 0) {
                    break;
                }
                String tag = l.substring(1, end);
                String key = tag.startsWith("!") ? tag.substring(1) : tag;
                if (!flags.containsKey(key)) {
                    break;
                }
                if (!test(tag)) {
                    show = false;
                }
                l = l.substring(end + 1);
            }
            if (!show) {
                continue;
            }
            boolean onlyPlaceholders = l.indexOf('{') >= 0
                    && Text.strip(l.replaceAll("\\{[^}]*}", "")).trim().isEmpty();
            String result = Text.replace(l, values);
            if (onlyPlaceholders && Text.strip(result).trim().isEmpty()) {
                // строка состояла только из плейсхолдеров, и они пустые — пропускаем
                continue;
            }
            for (String part : result.split("\n", -1)) {
                out.add(Text.color(part));
            }
        }
        return out;
    }
}
