package org.jestron.sanaldepo.vault;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.jestron.sanaldepo.SanalDepo;
import org.jestron.sanaldepo.config.Settings;
import org.jestron.sanaldepo.gui.MenuLayout;
import org.jestron.sanaldepo.gui.VaultHolder;
import org.jestron.sanaldepo.storage.Database;
import org.jestron.sanaldepo.storage.ItemCodec;
import org.jestron.sanaldepo.util.Sounds;
import org.jestron.sanaldepo.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Depo cekirdegi: onbellek, yukleme, kaydetme, acma/kapama.
 *
 * Is parcacigi modeli:
 *  - Tum durum (onbellek, envanterler) SADECE ana is parcacigindan degistirilir.
 *  - Tum veritabani islemleri TEK bir IO is parcacigindan sirayla yapilir. Bu sayede
 *    "cikista kaydet -> hizli tekrar giriste yukle" sirasi her zaman dogru olur.
 *  - Esya (de)serilestirme ana is parcaciginda yapilir, IO is parcacigina sadece byte[] gider.
 *
 * Dupe korumasi:
 *  - Bir depo icin tek bir canli envanter vardir; sahibi + yoneticiler ayni envanteri gorur.
 *  - Envanter, son izleyici kapatana kadar asil kaynaktir; sonra icerik kopyalanip kaydedilir.
 *  - Sahipsiz (onbellekten kopmus) bir envanterde tum tiklamalar iptal edilir.
 *  - Okunamayan (bozuk) depolar acilmaz ve uzerine asla yazilmaz.
 */
public final class VaultManager {

    public static final String PERM_USE = "sanaldepo.kullan";
    public static final String PERM_ADMIN_VIEW = "sanaldepo.admin.gor";
    public static final String PERM_ADMIN_EDIT = "sanaldepo.admin.duzenle";
    public static final String PERM_RELOAD = "sanaldepo.admin.yenile";
    public static final String PERM_BYPASS_CREATIVE = "sanaldepo.bypass.creative";
    public static final String PERM_BYPASS_WORLD = "sanaldepo.bypass.dunya";
    public static final String PERM_BYPASS_COOLDOWN = "sanaldepo.bypass.bekleme";
    public static final String PERM_BYPASS_BLACKLIST = "sanaldepo.bypass.yasakli";
    public static final String PERM_VAULT_PREFIX = "sanaldepo.depo.";

    /** Cevrimdisi oyuncu kaydinin (yonetici bakisi) bosta kalinca onbellekte tutulma suresi. */
    private static final long OFFLINE_IDLE_MS = 60_000L;

    private static final class ReturnInfo {
        final UUID owner;
        final String name;
        final boolean admin;
        final VaultHolder holder;

        ReturnInfo(UUID owner, String name, boolean admin, VaultHolder holder) {
            this.owner = owner;
            this.name = name;
            this.admin = admin;
            this.holder = holder;
        }
    }

    private final SanalDepo plugin;
    private final Database db;
    private final ItemCodec codec;
    private final ExecutorService io;

    private final Map<UUID, PlayerVaults> cache = new HashMap<>();
    private final Map<UUID, Long> lastOpen = new HashMap<>();
    private final Map<UUID, ReturnInfo> menuReturn = new HashMap<>();
    private final Map<UUID, Long> pendingReturn = new HashMap<>();
    private long returnCounter;
    private volatile boolean shuttingDown;

    public VaultManager(SanalDepo plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        this.codec = new ItemCodec(plugin.settings().preferPaperFormat);
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SanalDepo-IO");
            t.setDaemon(true);
            return t;
        });
        plugin.getLogger().info("Esya formati: " + (codec.isPaperFormat() ? "Paper (serializeAsBytes)" : "Bukkit"));
    }

    private Settings settings() {
        return plugin.settings();
    }

    /** IO is parcacigindan ana is parcacigina don. Kapanista sessizce yok sayilir. */
    private void sync(Runnable task) {
        if (shuttingDown || !plugin.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (IllegalPluginAccessException ignored) {
            // eklenti kapaniyor
        }
    }

    // =====================================================================
    //  Yetki / kisitlama
    // =====================================================================

    /** Oyuncunun kullanabilecegi depo sayisi (0..maksimum-hak). OP olsa bile maksimum-hak asilamaz. */
    public int allowedVaults(Player p) {
        if (!p.hasPermission(PERM_USE)) {
            return 0;
        }
        Settings s = settings();
        boolean opLimited = p.isOp() && !s.opGetsMax;
        for (int n = s.maxVaults; n >= 1; n--) {
            String perm = PERM_VAULT_PREFIX + n;
            if (p.hasPermission(perm) && (!opLimited || p.isPermissionSet(perm))) {
                return Math.max(n, s.defaultVaults);
            }
        }
        return s.defaultVaults;
    }

    /** Dunya / oyun modu kisitlamalari. */
    public boolean checkEnvironment(Player p) {
        Settings s = settings();
        if (!s.disabledWorlds.isEmpty()
                && s.disabledWorlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT))
                && !p.hasPermission(PERM_BYPASS_WORLD)) {
            plugin.messages().send(p, "dunya-engelli");
            Sounds.play(p, s, s.soundError);
            return false;
        }
        GameMode gm = p.getGameMode();
        if (gm == GameMode.SPECTATOR
                || (s.blockCreative && gm == GameMode.CREATIVE && !p.hasPermission(PERM_BYPASS_CREATIVE))) {
            plugin.messages().send(p, "creative");
            Sounds.play(p, s, s.soundError);
            return false;
        }
        return true;
    }

    private boolean checkCooldown(Player p) {
        Settings s = settings();
        if (s.openCooldownMs <= 0 || p.hasPermission(PERM_BYPASS_COOLDOWN)) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = lastOpen.get(p.getUniqueId());
        if (last != null && now - last < s.openCooldownMs) {
            plugin.messages().send(p, "bekle");
            return false;
        }
        lastOpen.put(p.getUniqueId(), now);
        return true;
    }

    // =====================================================================
    //  Onbellek / yukleme
    // =====================================================================

    public PlayerVaults getCached(UUID uuid) {
        return cache.get(uuid);
    }

    public boolean isReady(UUID uuid) {
        PlayerVaults pv = cache.get(uuid);
        return pv != null && pv.state == PlayerVaults.State.READY;
    }

    /**
     * Oyuncunun depolarini yukler (onbellekte varsa hemen doner).
     * callback ana is parcaciginda, durum READY veya FAILED iken cagrilir.
     */
    public void load(UUID uuid, String name, Consumer<PlayerVaults> callback) {
        PlayerVaults pv = cache.get(uuid);
        if (pv != null) {
            if (name != null) {
                pv.name = name;
            }
            pv.touch();
            if (pv.state == PlayerVaults.State.LOADING) {
                if (callback != null) {
                    pv.waiters.add(callback);
                }
            } else if (callback != null) {
                callback.accept(pv);
            }
            return;
        }
        if (shuttingDown) {
            return;
        }
        PlayerVaults created = new PlayerVaults(uuid, name);
        cache.put(uuid, created);
        if (callback != null) {
            created.waiters.add(callback);
        }
        io.execute(() -> {
            Map<Integer, byte[]> raw = null;
            Exception error = null;
            try {
                raw = db.loadAll(uuid);
            } catch (Exception e) {
                error = e;
            }
            final Map<Integer, byte[]> fRaw = raw;
            final Exception fError = error;
            sync(() -> finishLoad(created, fRaw, fError));
        });
    }

    private void finishLoad(PlayerVaults pv, Map<Integer, byte[]> raw, Exception error) {
        if (cache.get(pv.owner) != pv) {
            return;
        }
        if (error != null || raw == null) {
            plugin.getLogger().log(Level.SEVERE, "Depolar yuklenemedi: " + pv.name + " (" + pv.owner + ")", error);
            pv.state = PlayerVaults.State.FAILED;
            cache.remove(pv.owner); // bir sonraki denemede bastan yuklensin
        } else {
            for (Map.Entry<Integer, byte[]> e : raw.entrySet()) {
                int number = e.getKey();
                VaultSlot slot = pv.getOrCreate(number);
                slot.lastSaved = e.getValue();
                try {
                    slot.contents = codec.decode(e.getValue(), VaultSlot.SIZE);
                } catch (Exception ex) {
                    slot.corrupt = true;
                    slot.contents = new ItemStack[VaultSlot.SIZE];
                    plugin.getLogger().log(Level.SEVERE, "Depo verisi cozulemedi ve kilitlendi (veri silinmedi): "
                            + pv.name + " (" + pv.owner + ") depo #" + number, ex);
                }
            }
            pv.state = PlayerVaults.State.READY;
        }
        pv.touch();
        List<Consumer<PlayerVaults>> waiting = new ArrayList<>(pv.waiters);
        pv.waiters.clear();
        for (Consumer<PlayerVaults> c : waiting) {
            try {
                c.accept(pv);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Depo geri cagrisinda hata", t);
            }
        }
    }

    private boolean canEvict(PlayerVaults pv) {
        return pv.state != PlayerVaults.State.LOADING
                && pv.pendingWrites == 0
                && Bukkit.getPlayer(pv.owner) == null
                && !pv.hasOpenHolder()
                && !pv.hasFailedSave();
    }

    private void tryEvictNow(PlayerVaults pv) {
        if (cache.get(pv.owner) == pv && canEvict(pv)) {
            cache.remove(pv.owner);
        }
    }

    /** Periyodik temizlik: cevrimdisi ve bosta kalan kayitlari bellekten atar. */
    public void sweep() {
        long now = System.currentTimeMillis();
        Iterator<PlayerVaults> it = cache.values().iterator();
        while (it.hasNext()) {
            PlayerVaults pv = it.next();
            if (canEvict(pv) && (pv.evictAsap || now - pv.lastAccess > OFFLINE_IDLE_MS)) {
                it.remove();
            }
        }
    }

    // =====================================================================
    //  Kaydetme
    // =====================================================================

    private static ItemStack[] cloneContents(ItemStack[] live) {
        ItemStack[] copy = new ItemStack[VaultSlot.SIZE];
        if (live == null) {
            return copy;
        }
        int n = Math.min(live.length, VaultSlot.SIZE);
        for (int i = 0; i < n; i++) {
            ItemStack it = live[i];
            copy[i] = ItemCodec.isAir(it) ? null : it.clone();
        }
        return copy;
    }

    /**
     * Icerigin anlik kopyasini alir ve degistiyse veritabanina siraya koyar.
     * Ayni veri tekrar yazilmaz (gereksiz IO yok).
     */
    private void persist(PlayerVaults pv, VaultSlot slot, ItemStack[] live, boolean force) {
        if (slot.corrupt) {
            return;
        }
        ItemStack[] copy = cloneContents(live);
        slot.contents = copy;
        final byte[] data;
        if (ItemCodec.isEmpty(copy)) {
            data = null;
        } else {
            try {
                data = codec.encode(copy);
            } catch (Exception e) {
                slot.saveFailed = true;
                plugin.getLogger().log(Level.SEVERE, "Depo kodlanamadi: " + pv.name + " (" + pv.owner + ") depo #" + slot.number, e);
                return;
            }
        }
        if (!force && !slot.saveFailed && Arrays.equals(data, slot.lastSaved)) {
            return;
        }
        slot.lastSaved = data;
        final int seq = ++slot.writeSeq;
        final UUID owner = pv.owner;
        final int number = slot.number;
        pv.pendingWrites++;
        try {
            io.execute(() -> {
                boolean ok = true;
                try {
                    db.saveVault(owner, number, data);
                } catch (Exception e) {
                    ok = false;
                    plugin.getLogger().log(Level.SEVERE, "Depo kaydedilemedi (tekrar denenecek): " + owner + " depo #" + number, e);
                }
                final boolean fOk = ok;
                sync(() -> {
                    pv.pendingWrites--;
                    if (slot.writeSeq == seq) {
                        slot.saveFailed = !fOk;
                    }
                    if (pv.evictAsap) {
                        tryEvictNow(pv);
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            pv.pendingWrites--;
            slot.saveFailed = true;
            plugin.getLogger().severe("IO kuyrugu kapali, depo kaydedilemedi: " + owner + " depo #" + number);
        }
    }

    /** Otomatik kayit: acik depolar ve basarisiz kayitlar. */
    public void autosave() {
        for (PlayerVaults pv : new ArrayList<>(cache.values())) {
            if (pv.state != PlayerVaults.State.READY) {
                continue;
            }
            for (VaultSlot slot : pv.vaults.values()) {
                VaultHolder h = slot.holder;
                if (h != null) {
                    persist(pv, slot, h.getInventory().getContents(), false);
                } else if (slot.saveFailed) {
                    persist(pv, slot, slot.contents, true);
                }
            }
        }
    }

    // =====================================================================
    //  Acma / kapama
    // =====================================================================

    /** Menuyu acar (yukleme bitince). */
    public void openMenu(Player viewer, UUID owner, String ownerName, boolean admin) {
        if (shuttingDown) {
            return;
        }
        if (!isReady(owner)) {
            plugin.messages().send(viewer, "yukleniyor");
        }
        load(owner, ownerName, pv -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (pv.state != PlayerVaults.State.READY) {
                plugin.messages().send(viewer, "yukleme-hatasi");
                Sounds.play(viewer, settings(), settings().soundError);
                return;
            }
            plugin.menu().open(viewer, pv, admin);
        });
    }

    /**
     * Bir depoyu acar.
     *
     * @param admin    baskasinin deposunu yonetici olarak aciyor (hak siniri uygulanmaz)
     * @param fromMenu kapaninca menuye donulsun
     */
    public void openVault(Player viewer, UUID owner, String ownerName, int number, boolean admin, boolean fromMenu) {
        if (shuttingDown) {
            return;
        }
        if (number < 1 || number > MenuLayout.HARD_CAP) {
            plugin.messages().send(viewer, "gecersiz-numara", "%maks%", String.valueOf(admin ? MenuLayout.HARD_CAP : settings().maxVaults));
            return;
        }
        if (!checkEnvironment(viewer) || !checkCooldown(viewer)) {
            return;
        }
        if (!isReady(owner)) {
            plugin.messages().send(viewer, "yukleniyor");
        }
        load(owner, ownerName, pv -> {
            if (!viewer.isOnline() || shuttingDown) {
                return;
            }
            Settings s = settings();
            if (pv.state != PlayerVaults.State.READY) {
                plugin.messages().send(viewer, "yukleme-hatasi");
                Sounds.play(viewer, s, s.soundError);
                return;
            }
            boolean self = viewer.getUniqueId().equals(owner);
            if (!self && !(admin && viewer.hasPermission(PERM_ADMIN_VIEW))) {
                plugin.messages().send(viewer, "yetki-yok");
                return;
            }
            if (!admin || self) {
                int allowed = allowedVaults(viewer);
                if (number > allowed) {
                    plugin.messages().send(viewer, "depo-yok", "%no%", String.valueOf(number), "%hak%", String.valueOf(allowed));
                    Sounds.play(viewer, s, s.soundLocked);
                    return;
                }
            }
            VaultSlot existing = pv.get(number);
            if (existing != null && existing.corrupt) {
                plugin.messages().send(viewer, "depo-bozuk", "%no%", String.valueOf(number));
                Sounds.play(viewer, s, s.soundError);
                return;
            }

            // Once izleyicinin mevcut ekranini kapat: ayni depo zaten aciksa kapanis burada islenir
            // ve asagida guncel canli envanter alinir (eski/sahipsiz envantere yonlendirme olmaz).
            viewer.closeInventory();

            VaultSlot slot = pv.getOrCreate(number);
            VaultHolder holder = slot.holder;
            if (holder == null) {
                String name = pv.name != null ? pv.name : ownerName;
                String title = Text.format((admin && !self) ? s.vaultAdminTitle : s.vaultTitle,
                        "%no%", String.valueOf(number), "%oyuncu%", name == null ? "?" : name);
                holder = new VaultHolder(owner, name, number, VaultSlot.SIZE, title);
                holder.getInventory().setContents(cloneContents(slot.contents));
                slot.holder = holder;
            }
            boolean readOnly = admin && !self && !viewer.hasPermission(PERM_ADMIN_EDIT);
            holder.setReadOnly(viewer.getUniqueId(), readOnly);
            pv.touch();

            viewer.openInventory(holder.getInventory());

            if (!holder.getInventory().getViewers().contains(viewer)) {
                // Acilis baska bir eklenti tarafindan iptal edildi
                holder.setReadOnly(viewer.getUniqueId(), false);
                if (holder.getInventory().getViewers().isEmpty() && slot.holder == holder) {
                    slot.holder = null;
                }
                return;
            }
            if (fromMenu) {
                menuReturn.put(viewer.getUniqueId(), new ReturnInfo(owner, ownerName, admin && !self, holder));
            } else {
                menuReturn.remove(viewer.getUniqueId());
            }
            Sounds.play(viewer, s, s.soundVaultOpen);
            if (readOnly) {
                plugin.messages().send(viewer, "salt-okunur");
            }
            if (admin && !self && s.logAdminAccess) {
                plugin.getLogger().info(viewer.getName() + " -> " + pv.name + " (" + owner + ") adli oyuncunun #"
                        + number + " deposunu acti [" + (readOnly ? "salt okunur" : "duzenleme") + "]");
            }
        });
    }

    /** Envanter canli mi (onbellekteki guncel envanter mi)? Degilse tiklamalar iptal edilir. */
    public boolean isLive(VaultHolder holder) {
        PlayerVaults pv = cache.get(holder.getOwner());
        if (pv == null) {
            return false;
        }
        VaultSlot slot = pv.vaults.get(holder.getNumber());
        return slot != null && slot.holder == holder;
    }

    /** Bir izleyici depoyu kapatti. Son izleyiciyse icerik onbellege alinip kaydedilir. */
    public void handleClose(Player player, VaultHolder holder) {
        holder.setReadOnly(player.getUniqueId(), false);
        PlayerVaults pv = cache.get(holder.getOwner());
        if (pv == null) {
            return;
        }
        VaultSlot slot = pv.vaults.get(holder.getNumber());
        if (slot == null || slot.holder != holder) {
            return;
        }
        boolean othersViewing = false;
        for (HumanEntity v : holder.getInventory().getViewers()) {
            if (!v.getUniqueId().equals(player.getUniqueId())) {
                othersViewing = true;
                break;
            }
        }
        persist(pv, slot, holder.getInventory().getContents(), false);
        if (!othersViewing) {
            slot.holder = null;
        }
        pv.touch();
        if (pv.evictAsap) {
            tryEvictNow(pv);
        }
    }

    /** Menuden acilan depo kapaninca bir tik sonra menuye don (araya baska ekran girmediyse). */
    public void onVaultClosed(Player p, VaultHolder holder) {
        UUID id = p.getUniqueId();
        ReturnInfo ri = menuReturn.get(id);
        if (ri == null || ri.holder != holder) {
            return;
        }
        menuReturn.remove(id);
        if (shuttingDown || !settings().returnToMenu) {
            return;
        }
        final long token = ++returnCounter;
        pendingReturn.put(id, token);
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Long current = pendingReturn.get(id);
                if (current == null || current != token) {
                    return;
                }
                pendingReturn.remove(id);
                if (!p.isOnline() || p.isDead() || shuttingDown) {
                    return;
                }
                if (ri.admin && !p.hasPermission(PERM_ADMIN_VIEW)) {
                    return;
                }
                openMenu(p, ri.owner, ri.name, ri.admin);
            });
        } catch (IllegalPluginAccessException ignored) {
            pendingReturn.remove(id);
        }
    }

    /** Oyuncuya baska bir ekran acildi: bekleyen menuye donusu iptal et. */
    public void onInventoryOpen(Player p) {
        pendingReturn.remove(p.getUniqueId());
    }

    // =====================================================================
    //  Giris / cikis
    // =====================================================================

    public void handleJoin(Player p) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        PlayerVaults pv = cache.get(id);
        if (pv != null) {
            pv.evictAsap = false;
            pv.name = name;
            // MySQL (olasi coklu sunucu): baska sunucuda degismis olabilir, temizse tazele
            if (db.isMySql() && pv.state == PlayerVaults.State.READY && !pv.hasOpenHolder()
                    && !pv.hasFailedSave() && pv.pendingWrites == 0) {
                cache.remove(id);
            }
        }
        try {
            io.execute(() -> {
                try {
                    db.savePlayer(id, name);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Oyuncu kaydi guncellenemedi: " + name, e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            return;
        }
        int delay = settings().loadDelayTicks;
        if (delay > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    load(id, name, null);
                }
            }, delay);
        } else {
            load(id, name, null);
        }
    }

    public void handleQuit(Player p) {
        UUID id = p.getUniqueId();
        menuReturn.remove(id);
        pendingReturn.remove(id);
        lastOpen.remove(id);
        // Guvenlik: oyuncunun izledigi tum depolari kapat (kapanis olayi kacmis olsa bile)
        for (PlayerVaults pv : new ArrayList<>(cache.values())) {
            for (VaultSlot slot : new ArrayList<>(pv.vaults.values())) {
                VaultHolder h = slot.holder;
                if (h != null && h.getInventory().getViewers().contains(p)) {
                    handleClose(p, h);
                }
            }
        }
        PlayerVaults own = cache.get(id);
        if (own != null) {
            own.evictAsap = true;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> tryEvictNow(own));
            } catch (IllegalPluginAccessException ignored) {
                // kapanis
            }
        }
    }

    /** Cevrimdisi oyuncu adini uuid'ye cevirir (once cevrimici, sonra veritabani). */
    public void resolvePlayer(String name, Consumer<Database.PlayerRecord> callback) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            callback.accept(new Database.PlayerRecord(online.getUniqueId(), online.getName()));
            return;
        }
        if (!name.matches("[A-Za-z0-9_.\\-*]{1,32}")) {
            callback.accept(null);
            return;
        }
        try {
            io.execute(() -> {
                Database.PlayerRecord rec = null;
                try {
                    rec = db.findPlayer(name);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Oyuncu aranamadi: " + name, e);
                }
                final Database.PlayerRecord fRec = rec;
                sync(() -> callback.accept(fRec));
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // kapanis
        }
    }

    // =====================================================================
    //  Kapanis
    // =====================================================================

    /** Sunucu kapanisi / eklenti devre disi: her seyi kaydet ve bekle. */
    public void shutdown() {
        shuttingDown = true;
        int open = 0;
        for (PlayerVaults pv : new ArrayList<>(cache.values())) {
            if (pv.state != PlayerVaults.State.READY) {
                continue;
            }
            for (VaultSlot slot : new ArrayList<>(pv.vaults.values())) {
                VaultHolder h = slot.holder;
                if (h != null) {
                    open++;
                    persist(pv, slot, h.getInventory().getContents(), false);
                    slot.holder = null; // once kopar, sonra kapat -> kapanis olayi tekrar islemez
                    for (HumanEntity v : new ArrayList<>(h.getInventory().getViewers())) {
                        try {
                            v.closeInventory();
                        } catch (Throwable ignored) {
                            // devam
                        }
                    }
                } else if (slot.saveFailed) {
                    persist(pv, slot, slot.contents, true);
                }
            }
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(60, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Bazi depo kayitlari 60 saniyede tamamlanamadi!");
                io.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (open > 0) {
            plugin.getLogger().info(open + " acik depo kapatildi ve kaydedildi.");
        }
        cache.clear();
        menuReturn.clear();
        pendingReturn.clear();
        lastOpen.clear();
    }
}
