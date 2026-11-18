package org.jestron.sanaldepo.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jestron.sanaldepo.SanalDepo;
import org.jestron.sanaldepo.config.Settings;
import org.jestron.sanaldepo.util.Sounds;
import org.jestron.sanaldepo.util.Text;
import org.jestron.sanaldepo.vault.PlayerVaults;
import org.jestron.sanaldepo.vault.VaultManager;
import org.jestron.sanaldepo.vault.VaultSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 54 slotluk depo secim menusu.
 */
public final class MenuGui {

    private static final int BAR_LENGTH = 10;

    private final SanalDepo plugin;
    private final Map<UUID, MenuHolder> openMenus = new HashMap<>();
    private final Set<UUID> pendingOpen = new HashSet<>();

    public MenuGui(SanalDepo plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, PlayerVaults pv, boolean admin) {
        Settings s = plugin.settings();
        VaultManager vm = plugin.vaults();
        UUID owner = pv.getOwner();
        String name = pv.getName() != null ? pv.getName() : owner.toString().substring(0, 8);
        Player target = Bukkit.getPlayer(owner);

        int allowed;
        int shown;
        if (admin) {
            allowed = target != null ? vm.allowedVaults(target) : -1;
            shown = Math.min(MenuLayout.HARD_CAP, Math.max(s.maxVaults, pv.highestStored()));
        } else {
            allowed = vm.allowedVaults(viewer);
            if (allowed <= 0) {
                plugin.messages().send(viewer, "hic-depo-yok");
                Sounds.play(viewer, s, s.soundError);
                return;
            }
            shown = s.showLocked ? s.maxVaults : Math.min(allowed, s.maxVaults);
        }
        String allowedText = allowed >= 0 ? String.valueOf(allowed) : plugin.messages().get("bilinmiyor");

        String title = Text.format(admin ? s.menuAdminTitle : s.menuTitle,
                "%oyuncu%", name, "%hak%", allowedText, "%maks%", String.valueOf(s.maxVaults));
        MenuHolder holder = new MenuHolder(owner, name, admin);
        Inventory inv = Bukkit.createInventory(holder, MenuLayout.SIZE, title);
        holder.setInventory(inv);

        // ---- kenarlar ----
        ItemStack borderA = s.border.build(1);
        ItemStack borderB = s.borderAlt.build(1);
        ItemStack corner = s.corner.build(1);
        for (int slot = 0; slot < MenuLayout.SIZE; slot++) {
            if (!MenuLayout.isBorder(slot)) {
                continue;
            }
            ItemStack item = MenuLayout.isCorner(slot) ? corner : (MenuLayout.isAlternate(slot) ? borderB : borderA);
            if (item != null) {
                inv.setItem(slot, item.clone());
            }
        }

        // ---- depolar ----
        ItemStack empty = s.emptySlot.build(1);
        for (int i = 0; i < MenuLayout.INNER_SLOTS.length; i++) {
            int number = i + 1;
            int slot = MenuLayout.INNER_SLOTS[i];
            if (number > shown) {
                if (empty != null) {
                    inv.setItem(slot, empty.clone());
                }
                continue;
            }
            VaultSlot vs = pv.get(number);
            int used = vs == null ? 0 : vs.usedSlots();
            boolean corrupt = vs != null && vs.isCorrupt();
            int amount = s.numberAsAmount ? number : 1;
            int percent = (int) Math.round(used * 100.0D / VaultSlot.SIZE);
            String[] ph = {
                    "%no%", String.valueOf(number),
                    "%dolu%", String.valueOf(used),
                    "%bos%", String.valueOf(VaultSlot.SIZE - used),
                    "%boyut%", String.valueOf(VaultSlot.SIZE),
                    "%yuzde%", String.valueOf(percent),
                    "%bar%", bar(used),
                    "%oyuncu%", name
            };
            boolean unlocked = admin || number <= allowed;
            ItemStack item;
            if (unlocked) {
                item = s.unlockedVault.build(amount, ph);
                if (item != null && (corrupt || (admin && allowed >= 0 && number > allowed))) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        List<String> lore = meta.hasLore() && meta.getLore() != null
                                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                        lore.add("");
                        if (corrupt) {
                            lore.add(Text.color("&4&l⚠ &cVeri okunamadi - depo kilitli"));
                        }
                        if (admin && allowed >= 0 && number > allowed) {
                            lore.add(Text.color("&8(Oyuncunun hakki disinda)"));
                        }
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                }
            } else {
                item = s.lockedVault.build(amount, ph);
            }
            if (item != null) {
                inv.setItem(slot, item);
                holder.map(slot, number);
            }
        }

        // ---- bilgi ----
        if (s.infoEnabled) {
            String status = target != null ? plugin.messages().get("cevrimici") : plugin.messages().get("cevrimdisi");
            List<String> lore = admin && !s.infoAdminLore.isEmpty() ? s.infoAdminLore : s.info.getLore();
            ItemStack info = s.info.build(1, owner, lore,
                    "%oyuncu%", name,
                    "%uuid%", owner.toString(),
                    "%durum%", status,
                    "%hak%", allowedText,
                    "%maks%", String.valueOf(s.maxVaults),
                    "%kayitli%", String.valueOf(pv.storedCount()),
                    "%toplam%", String.valueOf(pv.totalUsedSlots()));
            if (info != null) {
                inv.setItem(s.infoSlot, info);
            }
        }

        viewer.openInventory(inv);
        if (inv.getViewers().contains(viewer)) {
            openMenus.put(viewer.getUniqueId(), holder);
            Sounds.play(viewer, s, s.soundMenuOpen);
        }
    }

    private static String bar(int used) {
        int filled = (int) Math.round(used * (double) BAR_LENGTH / VaultSlot.SIZE);
        if (used > 0 && filled == 0) {
            filled = 1;
        }
        double ratio = used / (double) VaultSlot.SIZE;
        String color = ratio < 0.5D ? "&a" : (ratio < 0.85D ? "&e" : "&c");
        StringBuilder sb = new StringBuilder(color);
        for (int i = 0; i < BAR_LENGTH; i++) {
            if (i == filled) {
                sb.append("&7");
            }
            sb.append('■');
        }
        return sb.toString();
    }

    /** Menu tiklamasi (olay zaten iptal edilmis durumda). */
    public void handleClick(Player p, MenuHolder holder, int rawSlot) {
        int number = holder.vaultAt(rawSlot);
        if (number <= 0) {
            return;
        }
        UUID id = p.getUniqueId();
        if (pendingOpen.contains(id)) {
            return;
        }
        Settings s = plugin.settings();
        VaultManager vm = plugin.vaults();
        if (holder.isAdmin()) {
            if (!p.hasPermission(VaultManager.PERM_ADMIN_VIEW)) {
                plugin.messages().send(p, "yetki-yok");
                p.closeInventory();
                return;
            }
        } else {
            int allowed = vm.allowedVaults(p);
            if (number > allowed) {
                plugin.messages().send(p, "depo-kilitli");
                Sounds.play(p, s, s.soundLocked);
                return;
            }
        }
        pendingOpen.add(id);
        // Envanter tiklama olayinin icinden baska envanter acmak yerine bir tik sonra ac
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingOpen.remove(id);
            if (!p.isOnline()) {
                return;
            }
            if (holder.isAdmin()) {
                vm.openVault(p, holder.getOwner(), holder.getOwnerName(), number, true, true);
            } else {
                vm.openVault(p, p.getUniqueId(), p.getName(), number, false, true);
            }
        });
    }

    public void onClose(Player p, MenuHolder holder) {
        openMenus.remove(p.getUniqueId(), holder);
    }

    public void onQuit(Player p) {
        openMenus.remove(p.getUniqueId());
        pendingOpen.remove(p.getUniqueId());
    }

    /** Eklenti kapanirken acik menuleri kapat (eski menude esya alinmasini onler). */
    public void closeAll() {
        for (UUID id : new ArrayList<>(openMenus.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                try {
                    p.closeInventory();
                } catch (Throwable ignored) {
                    // devam
                }
            }
        }
        openMenus.clear();
        pendingOpen.clear();
    }
}
