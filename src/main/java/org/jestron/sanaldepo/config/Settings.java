package org.jestron.sanaldepo.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jestron.sanaldepo.SanalDepo;
import org.jestron.sanaldepo.gui.MenuLayout;
import org.jestron.sanaldepo.util.ItemTemplate;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * config.yml'nin degismez (immutable) anlik goruntusu. /depo yenile ile yeniden olusturulur.
 */
public final class Settings {

    // ---- veritabani ----
    public final boolean mysql;
    public final String tablePrefix;
    public final String sqliteFile;
    public final String mysqlHost;
    public final int mysqlPort;
    public final String mysqlDatabase;
    public final String mysqlUser;
    public final String mysqlPassword;
    public final boolean mysqlSsl;
    public final int mysqlPoolSize;
    public final String mysqlExtra;
    public final int loadDelayTicks;
    public final boolean preferPaperFormat;

    // ---- depo ----
    public final int defaultVaults;
    public final int maxVaults;
    public final boolean opGetsMax;
    public final int autosaveMinutes;
    public final long openCooldownMs;
    public final boolean returnToMenu;
    public final boolean blockCreative;
    public final Set<String> disabledWorlds;
    public final Set<Material> blacklist;
    public final boolean logAdminAccess;

    // ---- menu ----
    public final String menuTitle;
    public final String menuAdminTitle;
    public final ItemTemplate border;
    public final ItemTemplate borderAlt;
    public final ItemTemplate corner;
    public final ItemTemplate emptySlot;
    public final boolean numberAsAmount;
    public final ItemTemplate unlockedVault;
    public final boolean showLocked;
    public final ItemTemplate lockedVault;
    public final boolean infoEnabled;
    public final int infoSlot;
    public final ItemTemplate info;
    public final List<String> infoAdminLore;

    // ---- depo ekrani ----
    public final String vaultTitle;
    public final String vaultAdminTitle;

    // ---- sesler ----
    public final String soundMenuOpen;
    public final String soundVaultOpen;
    public final String soundVaultClose;
    public final String soundLocked;
    public final String soundError;
    public final float soundVolume;
    public final float soundPitch;

    public Settings(SanalDepo plugin) {
        FileConfiguration c = plugin.getConfig();
        Logger log = plugin.getLogger();

        String type = c.getString("veritabani.tur", "SQLITE").trim().toUpperCase(Locale.ROOT);
        this.mysql = type.equals("MYSQL") || type.equals("MARIADB");
        String prefix = c.getString("veritabani.tablo-oneki", "sanaldepo_");
        if (prefix == null || !prefix.matches("[A-Za-z0-9_]{0,32}")) {
            log.warning("Gecersiz tablo-oneki, 'sanaldepo_' kullaniliyor.");
            prefix = "sanaldepo_";
        }
        this.tablePrefix = prefix;
        this.sqliteFile = c.getString("veritabani.sqlite.dosya", "depolar.db");
        this.mysqlHost = c.getString("veritabani.mysql.host", "localhost");
        this.mysqlPort = c.getInt("veritabani.mysql.port", 3306);
        this.mysqlDatabase = c.getString("veritabani.mysql.veritabani", "minecraft");
        this.mysqlUser = c.getString("veritabani.mysql.kullanici", "root");
        this.mysqlPassword = c.getString("veritabani.mysql.sifre", "");
        this.mysqlSsl = c.getBoolean("veritabani.mysql.ssl", false);
        this.mysqlPoolSize = Math.max(2, Math.min(32, c.getInt("veritabani.mysql.havuz-boyutu", 6)));
        this.mysqlExtra = c.getString("veritabani.mysql.ek-ayarlar", "");
        this.loadDelayTicks = Math.max(0, c.getInt("veritabani.yukleme-gecikmesi-tick", 0));
        this.preferPaperFormat = !"BUKKIT".equalsIgnoreCase(c.getString("veritabani.esya-formati", "AUTO"));

        int max = c.getInt("depo.maksimum-hak", MenuLayout.HARD_CAP);
        if (max < 1 || max > MenuLayout.HARD_CAP) {
            log.warning("depo.maksimum-hak 1-" + MenuLayout.HARD_CAP + " arasinda olmali, " + MenuLayout.HARD_CAP + " ile sinirlandi.");
            max = Math.max(1, Math.min(MenuLayout.HARD_CAP, max));
        }
        this.maxVaults = max;
        this.defaultVaults = Math.max(0, Math.min(max, c.getInt("depo.varsayilan-hak", 1)));
        this.opGetsMax = c.getBoolean("depo.op-maksimum-alir", true);
        this.autosaveMinutes = Math.max(1, c.getInt("depo.otomatik-kayit-dakika", 3));
        this.openCooldownMs = Math.max(0L, c.getLong("depo.acma-bekleme-ms", 350L));
        this.returnToMenu = c.getBoolean("depo.kapaninca-menuye-don", true);
        this.blockCreative = c.getBoolean("depo.creative-engelle", true);

        Set<String> worlds = new HashSet<>();
        for (String w : c.getStringList("depo.engelli-dunyalar")) {
            worlds.add(w.toLowerCase(Locale.ROOT));
        }
        this.disabledWorlds = Collections.unmodifiableSet(worlds);

        Set<Material> bl = EnumSet.noneOf(Material.class);
        for (String raw : c.getStringList("depo.yasakli-esyalar")) {
            Material m = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (m == null) {
                log.warning("depo.yasakli-esyalar icinde gecersiz malzeme: " + raw);
            } else {
                bl.add(m);
            }
        }
        this.blacklist = Collections.unmodifiableSet(bl);
        this.logAdminAccess = c.getBoolean("depo.admin-erisim-log", true);

        this.menuTitle = c.getString("menu.baslik", "&8&lDepolarin");
        this.menuAdminTitle = c.getString("menu.admin-baslik", "&4&l%oyuncu% &8» &8&lDepolar");
        this.border = ItemTemplate.read(c.getConfigurationSection("menu.kenar"), null,
                Material.GRAY_STAINED_GLASS_PANE, log, "menu.kenar");
        ConfigurationSection alt = c.getConfigurationSection("menu.kenar-alternatif");
        this.borderAlt = alt == null ? border : ItemTemplate.read(alt, null,
                Material.BLACK_STAINED_GLASS_PANE, log, "menu.kenar-alternatif");
        this.corner = ItemTemplate.read(c.getConfigurationSection("menu.kose"), null,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE, log, "menu.kose");
        this.emptySlot = ItemTemplate.read(c.getConfigurationSection("menu.bos-slot"), null,
                Material.AIR, log, "menu.bos-slot");
        this.numberAsAmount = c.getBoolean("menu.numarayi-adet-olarak-goster", true);
        this.unlockedVault = ItemTemplate.read(c.getConfigurationSection("menu.acik-depo"), "lore",
                Material.CHEST, log, "menu.acik-depo");
        this.showLocked = c.getBoolean("menu.kilitli-depo.goster", true);
        this.lockedVault = ItemTemplate.read(c.getConfigurationSection("menu.kilitli-depo"), "lore",
                Material.BARRIER, log, "menu.kilitli-depo");

        this.infoEnabled = c.getBoolean("menu.bilgi.aktif", true);
        int slot = c.getInt("menu.bilgi.slot", 49);
        if (slot < 0 || slot >= MenuLayout.SIZE || !MenuLayout.isBorder(slot)) {
            log.warning("menu.bilgi.slot bir kenar slotu olmali (" + slot + "), 49 kullaniliyor.");
            slot = 49;
        }
        this.infoSlot = slot;
        this.info = ItemTemplate.read(c.getConfigurationSection("menu.bilgi"), "lore",
                Material.BOOK, log, "menu.bilgi");
        this.infoAdminLore = c.getStringList("menu.bilgi.admin-lore");

        this.vaultTitle = c.getString("depo-ekrani.baslik", "&8Depo &6#%no%");
        this.vaultAdminTitle = c.getString("depo-ekrani.admin-baslik", "&c%oyuncu% &8» &8Depo &6#%no%");

        this.soundMenuOpen = c.getString("sesler.menu-ac", "");
        this.soundVaultOpen = c.getString("sesler.depo-ac", "");
        this.soundVaultClose = c.getString("sesler.depo-kapat", "");
        this.soundLocked = c.getString("sesler.kilitli", "");
        this.soundError = c.getString("sesler.hata", "");
        this.soundVolume = (float) c.getDouble("sesler.ses-seviyesi", 0.7D);
        this.soundPitch = (float) c.getDouble("sesler.perde", 1.0D);
    }
}
