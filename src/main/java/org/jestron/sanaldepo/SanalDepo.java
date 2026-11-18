package org.jestron.sanaldepo;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jestron.sanaldepo.command.DepoCommand;
import org.jestron.sanaldepo.config.Messages;
import org.jestron.sanaldepo.config.Settings;
import org.jestron.sanaldepo.gui.MenuGui;
import org.jestron.sanaldepo.listener.InventoryListener;
import org.jestron.sanaldepo.listener.PlayerListener;
import org.jestron.sanaldepo.storage.Database;
import org.jestron.sanaldepo.vault.VaultManager;

import java.util.logging.Level;

/**
 * SanalDepo - PlayerVaults mantiginda sanal depo eklentisi.
 *
 * @author Jestron
 */
public final class SanalDepo extends JavaPlugin {

    private static final long SWEEP_INTERVAL_TICKS = 20L * 30L;

    private Settings settings;
    private Messages messages;
    private Database database;
    private VaultManager vaultManager;
    private MenuGui menuGui;
    private BukkitTask autosaveTask;
    private BukkitTask sweepTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        this.settings = new Settings(this);
        this.messages = new Messages(this);

        this.database = new Database(this, settings);
        try {
            database.connect();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Veritabanina baglanilamadi! Eklenti devre disi birakiliyor.", t);
            database.close();
            database = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.vaultManager = new VaultManager(this, database);
        this.menuGui = new MenuGui(this);

        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand cmd = getCommand("depo");
        if (cmd != null) {
            DepoCommand executor = new DepoCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        startTasks();

        // /reload veya PlugMan sonrasi cevrimici oyunculari hazirla
        for (Player p : Bukkit.getOnlinePlayers()) {
            vaultManager.handleJoin(p);
        }

        getLogger().info("SanalDepo v" + getDescription().getVersion() + " aktif. Veritabani: "
                + (settings.mysql ? "MySQL" : "SQLite") + ", maksimum depo: " + settings.maxVaults);
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        if (menuGui != null) {
            menuGui.closeAll();
        }
        if (vaultManager != null) {
            vaultManager.shutdown();
        }
        if (database != null) {
            database.close();
        }
    }

    private void startTasks() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        long autosaveTicks = 20L * 60L * settings.autosaveMinutes;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, () -> vaultManager.autosave(), autosaveTicks, autosaveTicks);
        sweepTask = Bukkit.getScheduler().runTaskTimer(this, () -> vaultManager.sweep(), SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
    }

    /** /depo yenile: config + mesajlar. Veritabani ayarlari icin sunucu yeniden baslatilmalidir. */
    public void reloadAll() {
        reloadConfig();
        this.settings = new Settings(this);
        this.messages.reload();
        startTasks();
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public VaultManager vaults() {
        return vaultManager;
    }

    public MenuGui menu() {
        return menuGui;
    }
}
