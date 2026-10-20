package dev.shardbuyer.util;

import org.bukkit.entity.Player;

/** Звук в формате "entity.experience_orb.pickup:1:1.2" (название:громкость:высота). Пустая строка — без звука. */
public final class SoundSpec {

    public static final SoundSpec NONE = new SoundSpec(null, 0, 0);

    private final String sound;
    private final float volume;
    private final float pitch;

    private SoundSpec(String sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static SoundSpec parse(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("none")) {
            return NONE;
        }
        String[] p = raw.trim().split(":");
        // поддержка "minecraft:entity.x.y:1:1"
        int offset = 0;
        String name = p[0];
        if (p.length > 1 && p[0].equalsIgnoreCase("minecraft")) {
            name = p[0] + ":" + p[1];
            offset = 1;
        }
        float vol = 1f;
        float pitch = 1f;
        try {
            if (p.length > 1 + offset) {
                vol = Float.parseFloat(p[1 + offset]);
            }
            if (p.length > 2 + offset) {
                pitch = Float.parseFloat(p[2 + offset]);
            }
        } catch (NumberFormatException ignored) {
            // по умолчанию
        }
        return new SoundSpec(name.toLowerCase(java.util.Locale.ROOT), vol, pitch);
    }

    public void play(Player player) {
        if (sound == null || player == null) {
            return;
        }
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Throwable ignored) {
            // неверное имя звука — молча игнорируем
        }
    }
}
