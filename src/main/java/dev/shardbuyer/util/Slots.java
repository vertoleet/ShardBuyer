package dev.shardbuyer.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Парсинг слотов: 5 / "0-8,45-53" / [0, 1, "10-16"]. */
public final class Slots {

    private Slots() {
    }

    public static int[] parse(Object raw) {
        Set<Integer> out = new LinkedHashSet<>();
        collect(raw, out);
        int[] arr = new int[out.size()];
        int i = 0;
        for (int v : out) {
            arr[i++] = v;
        }
        return arr;
    }

    private static void collect(Object raw, Set<Integer> out) {
        if (raw == null) {
            return;
        }
        if (raw instanceof Number n) {
            out.add(n.intValue());
            return;
        }
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                collect(o, out);
            }
            return;
        }
        for (String part : String.valueOf(raw).split(",")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int dash = p.indexOf('-', 1);
            try {
                if (dash > 0) {
                    int a = Integer.parseInt(p.substring(0, dash).trim());
                    int b = Integer.parseInt(p.substring(dash + 1).trim());
                    for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
                        out.add(i);
                    }
                } else {
                    out.add(Integer.parseInt(p));
                }
            } catch (NumberFormatException ignored) {
                // пропускаем мусор
            }
        }
    }

    public static List<Integer> list(Object raw) {
        List<Integer> l = new ArrayList<>();
        for (int i : parse(raw)) {
            l.add(i);
        }
        return l;
    }
}
