package org.jestron.sanaldepo.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jestron.sanaldepo.SanalDepo;
import org.jestron.sanaldepo.config.Settings;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite / MySQL erisim katmani. TUM metotlar engelleyicidir (blocking) ve
 * yalnizca VaultManager'in tek IO is parcacigindan (veya kapanista) cagrilir.
 *
 * Tablolar:
 *   {onek}vaults  (uuid, vault, data BLOB, updated)  PK(uuid, vault)  -> oyuncu basina tek sorguda tum depolar
 *   {onek}players (uuid, name, name_lower, last_seen) -> cevrimdisi oyuncu adi -> uuid cozumleme
 *
 * Bos depolar icin satir tutulmaz (yer tasarrufu).
 */
public final class Database {

    public static final class PlayerRecord {
        public final UUID uuid;
        public final String name;

        public PlayerRecord(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private final SanalDepo plugin;
    private final Settings settings;
    private final String vaultTable;
    private final String playerTable;
    private HikariDataSource dataSource;

    private String sqlUpsertVault;
    private String sqlUpsertPlayer;

    public Database(SanalDepo plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.vaultTable = settings.tablePrefix + "vaults";
        this.playerTable = settings.tablePrefix + "players";
    }

    public boolean isMySql() {
        return settings.mysql;
    }

    public void connect() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("SanalDepo-Pool");
        if (settings.mysql) {
            String driver = findClass("com.mysql.cj.jdbc.Driver") ? "com.mysql.cj.jdbc.Driver"
                    : findClass("org.mariadb.jdbc.Driver") ? "org.mariadb.jdbc.Driver"
                    : "com.mysql.jdbc.Driver";
            String protocol = driver.startsWith("org.mariadb") ? "jdbc:mariadb://" : "jdbc:mysql://";
            StringBuilder url = new StringBuilder(protocol)
                    .append(settings.mysqlHost).append(':').append(settings.mysqlPort)
                    .append('/').append(settings.mysqlDatabase)
                    .append("?useSSL=").append(settings.mysqlSsl);
            if (settings.mysqlExtra != null && !settings.mysqlExtra.trim().isEmpty()) {
                String extra = settings.mysqlExtra.trim();
                url.append(extra.startsWith("&") ? "" : "&").append(extra);
            }
            cfg.setDriverClassName(driver);
            cfg.setJdbcUrl(url.toString());
            cfg.setUsername(settings.mysqlUser);
            cfg.setPassword(settings.mysqlPassword);
            cfg.setMaximumPoolSize(settings.mysqlPoolSize);
            cfg.setMinimumIdle(Math.min(2, settings.mysqlPoolSize));
            cfg.setMaxLifetime(1_800_000L);
            cfg.setKeepaliveTime(300_000L);
            cfg.setConnectionTimeout(10_000L);
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            cfg.addDataSourceProperty("prepStmtCacheSize", "64");
            cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "1024");
            cfg.addDataSourceProperty("useServerPrepStmts", "true");
            cfg.addDataSourceProperty("rewriteBatchedStatements", "true");
            cfg.addDataSourceProperty("tcpKeepAlive", "true");

            sqlUpsertVault = "INSERT INTO " + vaultTable + " (uuid, vault, data, updated) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE data = VALUES(data), updated = VALUES(updated)";
            sqlUpsertPlayer = "INSERT INTO " + playerTable + " (uuid, name, name_lower, last_seen) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE name = VALUES(name), name_lower = VALUES(name_lower), last_seen = VALUES(last_seen)";
        } else {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IllegalStateException("Eklenti klasoru olusturulamadi: " + folder);
            }
            File file = new File(folder, settings.sqliteFile);
            cfg.setDriverClassName("org.sqlite.JDBC");
            cfg.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            // SQLite tek yazari destekler; tek baglanti + tek IO is parcacigi = kilitlenme yok
            cfg.setMaximumPoolSize(1);
            cfg.setMinimumIdle(1);
            cfg.setConnectionTimeout(30_000L);
            cfg.setMaxLifetime(0L);
            cfg.setIdleTimeout(0L);
            cfg.addDataSourceProperty("journal_mode", "WAL");
            cfg.addDataSourceProperty("synchronous", "NORMAL");
            cfg.addDataSourceProperty("busy_timeout", "10000");
            cfg.addDataSourceProperty("foreign_keys", "false");

            sqlUpsertVault = "INSERT OR REPLACE INTO " + vaultTable + " (uuid, vault, data, updated) VALUES (?, ?, ?, ?)";
            sqlUpsertPlayer = "INSERT OR REPLACE INTO " + playerTable + " (uuid, name, name_lower, last_seen) VALUES (?, ?, ?, ?)";
        }

        this.dataSource = new HikariDataSource(cfg);
        createTables();
    }

    private static boolean findClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
            if (settings.mysql) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + vaultTable + " ("
                        + "uuid CHAR(36) NOT NULL,"
                        + "vault TINYINT UNSIGNED NOT NULL,"
                        + "data MEDIUMBLOB NOT NULL,"
                        + "updated BIGINT NOT NULL,"
                        + "PRIMARY KEY (uuid, vault)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + playerTable + " ("
                        + "uuid CHAR(36) NOT NULL,"
                        + "name VARCHAR(32) NOT NULL,"
                        + "name_lower VARCHAR(32) NOT NULL,"
                        + "last_seen BIGINT NOT NULL,"
                        + "PRIMARY KEY (uuid),"
                        + "KEY idx_" + playerTable + "_name (name_lower)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            } else {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + vaultTable + " ("
                        + "uuid VARCHAR(36) NOT NULL,"
                        + "vault INTEGER NOT NULL,"
                        + "data BLOB NOT NULL,"
                        + "updated BIGINT NOT NULL,"
                        + "PRIMARY KEY (uuid, vault)"
                        + ") WITHOUT ROWID");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + playerTable + " ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "name VARCHAR(32) NOT NULL,"
                        + "name_lower VARCHAR(32) NOT NULL,"
                        + "last_seen BIGINT NOT NULL"
                        + ")");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + playerTable + "_name ON "
                        + playerTable + " (name_lower)");
            }
        }
    }

    /** Oyuncunun tum dolu depolarini tek sorguda getirir: depo no -> ham veri. */
    public Map<Integer, byte[]> loadAll(UUID uuid) throws SQLException {
        Map<Integer, byte[]> map = new HashMap<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT vault, data FROM " + vaultTable + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt(1), rs.getBytes(2));
                }
            }
        }
        return map;
    }

    public void saveVault(UUID uuid, int vault, byte[] data) throws SQLException {
        try (Connection con = dataSource.getConnection()) {
            if (data == null) {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + vaultTable + " WHERE uuid = ? AND vault = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, vault);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement(sqlUpsertVault)) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, vault);
                    ps.setBytes(3, data);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }
        }
    }

    public void savePlayer(UUID uuid, String name) throws SQLException {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sqlUpsertPlayer)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, name.toLowerCase(Locale.ROOT));
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    /** Ada gore (buyuk/kucuk harf duyarsiz) en son gorulen oyuncuyu bulur. */
    public PlayerRecord findPlayer(String name) throws SQLException {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT uuid, name FROM " + playerTable
                     + " WHERE name_lower = ? ORDER BY last_seen DESC LIMIT 1")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerRecord(UUID.fromString(rs.getString(1)), rs.getString(2));
                }
            }
        }
        return null;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
