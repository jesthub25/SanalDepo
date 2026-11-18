package org.jestron.sanaldepo.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jestron.sanaldepo.SanalDepo;

/**
 * Giriste depolari onceden yukler, cikista kaydeder ve bellekten atar.
 */
public final class PlayerListener implements Listener {

    private final SanalDepo plugin;

    public PlayerListener(SanalDepo plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        plugin.vaults().handleJoin(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.menu().onQuit(e.getPlayer());
        plugin.vaults().handleQuit(e.getPlayer());
    }
}
