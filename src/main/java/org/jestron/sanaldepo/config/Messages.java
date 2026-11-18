package org.jestron.sanaldepo.config;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jestron.sanaldepo.SanalDepo;
import org.jestron.sanaldepo.util.Text;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * mesajlar.yml yoneticisi. Eksik anahtarlar jar icindeki varsayilanlardan okunur.
 */
public final class Messages {

    private final SanalDepo plugin;
    private YamlConfiguration config;
    private String prefix = "";

    public Messages(SanalDepo plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "mesajlar.yml");
        if (!file.exists()) {
            plugin.saveResource("mesajlar.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("mesajlar.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                cfg.setDefaults(defaults);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Varsayilan mesajlar okunamadi", e);
        }
        this.config = cfg;
        this.prefix = cfg.getString("onek", "");
    }

    public String raw(String key) {
        return config.getString(key, key);
    }

    public String get(String key, String... placeholders) {
        return Text.format(raw(key), placeholders);
    }

    public void send(CommandSender to, String key, String... placeholders) {
        String msg = raw(key);
        if (msg == null || msg.isEmpty()) {
            return;
        }
        to.sendMessage(Text.format(prefix + msg, placeholders));
    }

    public void sendList(CommandSender to, String key, String... placeholders) {
        List<String> lines = config.getStringList(key);
        for (String line : lines) {
            to.sendMessage(Text.format(line, placeholders));
        }
    }
}
