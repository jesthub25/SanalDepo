package org.jestron.sanaldepo.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jestron.sanaldepo.SanalDepo;
import org.jestron.sanaldepo.config.Settings;
import org.jestron.sanaldepo.gui.MenuHolder;
import org.jestron.sanaldepo.gui.VaultHolder;
import org.jestron.sanaldepo.util.Sounds;
import org.jestron.sanaldepo.vault.VaultManager;

import java.util.Set;

/**
 * Menu ve depo envanterlerinin tum tiklama / surukleme / kapama olaylari.
 * Not: 1.21'de degisen InventoryView API'sine dokunulmaz (getView() kullanilmaz).
 */
public final class InventoryListener implements Listener {

    private final SanalDepo plugin;

    public InventoryListener(SanalDepo plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getInventory();
        InventoryHolder h = top.getHolder();

        if (h instanceof MenuHolder) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            if (!(e.getWhoClicked() instanceof Player)) {
                return;
            }
            int raw = e.getRawSlot();
            if (raw < 0 || raw >= top.getSize()) {
                return;
            }
            plugin.menu().handleClick((Player) e.getWhoClicked(), (MenuHolder) h, raw);
            return;
        }

        if (!(h instanceof VaultHolder)) {
            return;
        }
        VaultHolder vh = (VaultHolder) h;
        if (!(e.getWhoClicked() instanceof Player)) {
            e.setCancelled(true);
            return;
        }
        Player p = (Player) e.getWhoClicked();

        if (!plugin.vaults().isLive(vh)) {
            // Sahipsiz envanter: hicbir sey yapilamaz, kapat
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }
        if (vh.isReadOnly(p.getUniqueId())) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        }
        if (violatesBlacklist(e, top, p)) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            plugin.messages().send(p, "yasakli-esya");
            Sounds.play(p, plugin.settings(), plugin.settings().soundError);
        }
    }

    private boolean violatesBlacklist(InventoryClickEvent e, Inventory top, Player p) {
        Set<Material> bl = plugin.settings().blacklist;
        if (bl.isEmpty() || p.hasPermission(VaultManager.PERM_BYPASS_BLACKLIST)) {
            return false;
        }
        int raw = e.getRawSlot();
        if (raw < 0) {
            return false;
        }
        boolean inTop = raw < top.getSize();
        ClickType click = e.getClick();
        if (inTop) {
            // imlecteki esyayi koyma / takas
            if (blocked(e.getCursor(), bl)) {
                return true;
            }
            // 1-9 tuslari ile hizli takas
            if (click == ClickType.NUMBER_KEY) {
                int btn = e.getHotbarButton();
                if (btn >= 0 && blocked(p.getInventory().getItem(btn), bl)) {
                    return true;
                }
            }
            // F tusu (sol el) takasi
            if (click == ClickType.SWAP_OFFHAND && blocked(p.getInventory().getItemInOffHand(), bl)) {
                return true;
            }
            return false;
        }
        // alt envanterden shift ile depoya aktarma
        return e.isShiftClick() && blocked(e.getCurrentItem(), bl);
    }

    private static boolean blocked(ItemStack item, Set<Material> bl) {
        return item != null && item.getAmount() > 0 && bl.contains(item.getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getInventory();
        InventoryHolder h = top.getHolder();
        if (h instanceof MenuHolder) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        }
        if (!(h instanceof VaultHolder)) {
            return;
        }
        VaultHolder vh = (VaultHolder) h;
        if (!(e.getWhoClicked() instanceof Player)) {
            e.setCancelled(true);
            return;
        }
        Player p = (Player) e.getWhoClicked();
        if (!plugin.vaults().isLive(vh) || vh.isReadOnly(p.getUniqueId())) {
            e.setCancelled(true);
            e.setResult(Event.Result.DENY);
            return;
        }
        Set<Material> bl = plugin.settings().blacklist;
        if (!bl.isEmpty() && !p.hasPermission(VaultManager.PERM_BYPASS_BLACKLIST) && blocked(e.getOldCursor(), bl)) {
            int size = top.getSize();
            for (int raw : e.getRawSlots()) {
                if (raw < size) {
                    e.setCancelled(true);
                    e.setResult(Event.Result.DENY);
                    plugin.messages().send(p, "yasakli-esya");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getPlayer();
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof VaultHolder) {
            VaultHolder vh = (VaultHolder) h;
            plugin.vaults().handleClose(p, vh);
            plugin.vaults().onVaultClosed(p, vh);
            Settings s = plugin.settings();
            Sounds.play(p, s, s.soundVaultClose);
        } else if (h instanceof MenuHolder) {
            plugin.menu().onClose(p, (MenuHolder) h);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player) {
            plugin.vaults().onInventoryOpen((Player) e.getPlayer());
        }
    }
}
