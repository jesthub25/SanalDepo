package org.jestron.sanaldepo.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Config'den okunan menu simgesi sablonu.
 */
public final class ItemTemplate {

    private final Material material;
    private final String name;
    private final List<String> lore;

    public ItemTemplate(Material material, String name, List<String> lore) {
        this.material = material;
        this.name = name;
        this.lore = lore == null ? Collections.emptyList() : lore;
    }

    public static ItemTemplate read(ConfigurationSection sec, String loreKey, Material fallback, Logger log, String path) {
        if (sec == null) {
            return new ItemTemplate(fallback, " ", Collections.emptyList());
        }
        Material mat = parseMaterial(sec.getString("malzeme"), fallback, log, path + ".malzeme");
        String name = sec.getString("isim", " ");
        List<String> lore = loreKey == null ? Collections.emptyList() : sec.getStringList(loreKey);
        return new ItemTemplate(mat, name, lore);
    }

    public static Material parseMaterial(String raw, Material fallback, Logger log, String path) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        Material m = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (m == null) {
            if (log != null) {
                log.warning("Gecersiz malzeme '" + raw + "' (" + path + "), varsayilan kullaniliyor: " + fallback);
            }
            return fallback;
        }
        return m;
    }

    public Material getMaterial() {
        return material;
    }

    public boolean isAir() {
        return material == null || material.isAir();
    }

    public List<String> getLore() {
        return lore;
    }

    public ItemStack build(int amount, String... placeholders) {
        return build(amount, null, lore, placeholders);
    }

    /**
     * @param skullOwner PLAYER_HEAD ise kafanin sahibi (null olabilir)
     * @param loreLines  kullanilacak lore satirlari
     */
    public ItemStack build(int amount, UUID skullOwner, List<String> loreLines, String... placeholders) {
        if (isAir()) {
            return null;
        }
        int max = Math.max(1, material.getMaxStackSize());
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(amount, max)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(Text.format(name, placeholders));
            }
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.setLore(Text.format(loreLines, placeholders));
            }
            for (ItemFlag flag : ItemFlag.values()) {
                try {
                    meta.addItemFlags(flag);
                } catch (Throwable ignored) {
                    // bazi surumlerde bazi bayraklar desteklenmeyebilir
                }
            }
            if (skullOwner != null && meta instanceof SkullMeta) {
                try {
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(skullOwner);
                    ((SkullMeta) meta).setOwningPlayer(owner);
                } catch (Throwable ignored) {
                    // kafa dokusu alinamazsa varsayilan kafa kalir
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
