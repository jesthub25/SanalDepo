package org.jestron.sanaldepo.util;

import org.bukkit.entity.Player;
import org.jestron.sanaldepo.config.Settings;

/**
 * Ses calma yardimcisi. Enum yerine anahtar (ornegin "block.chest.open") kullanir;
 * boylece 1.16 - 1.21+ arasi Sound API degisikliklerinden etkilenmez.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, Settings settings, String key) {
        if (player == null || key == null || key.isEmpty()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), key, settings.soundVolume, settings.soundPitch);
        } catch (Throwable ignored) {
            // gecersiz ses anahtari: sessizce gec
        }
    }
}
