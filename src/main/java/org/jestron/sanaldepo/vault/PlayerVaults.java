package org.jestron.sanaldepo.vault;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bir oyuncunun tum depolarinin onbellek kaydi. Yalnizca ana is parcacigindan erisilir.
 */
public final class PlayerVaults {

    public enum State { LOADING, READY, FAILED }

    final UUID owner;
    String name;
    State state = State.LOADING;
    final Map<Integer, VaultSlot> vaults = new HashMap<>();
    final List<Consumer<PlayerVaults>> waiters = new ArrayList<>(2);
    /** Henuz tamamlanmamis veritabani yazma sayisi. 0 olmadan onbellekten silinmez. */
    int pendingWrites;
    /** Son erisim zamani (cevrimdisi oyuncu kayitlarinin bosta kalinca silinmesi icin). */
    long lastAccess = System.currentTimeMillis();
    /** Sahibi cikti: yazmalar biter bitmez onbellekten sil. */
    boolean evictAsap;

    PlayerVaults(UUID owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    void touch() {
        lastAccess = System.currentTimeMillis();
    }

    public UUID getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public State getState() {
        return state;
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public VaultSlot get(int number) {
        return vaults.get(number);
    }

    VaultSlot getOrCreate(int number) {
        return vaults.computeIfAbsent(number, VaultSlot::new);
    }

    public Collection<VaultSlot> all() {
        return vaults.values();
    }

    /** Icinde esya olan (veya bozuk) depo sayisi. */
    public int storedCount() {
        int c = 0;
        for (VaultSlot s : vaults.values()) {
            if (s.corrupt || s.usedSlots() > 0) {
                c++;
            }
        }
        return c;
    }

    public int highestStored() {
        int h = 0;
        for (VaultSlot s : vaults.values()) {
            if (s.number > h && (s.corrupt || s.usedSlots() > 0)) {
                h = s.number;
            }
        }
        return h;
    }

    public int totalUsedSlots() {
        int c = 0;
        for (VaultSlot s : vaults.values()) {
            c += s.usedSlots();
        }
        return c;
    }

    boolean hasOpenHolder() {
        for (VaultSlot s : vaults.values()) {
            if (s.holder != null) {
                return true;
            }
        }
        return false;
    }

    boolean hasFailedSave() {
        for (VaultSlot s : vaults.values()) {
            if (s.saveFailed) {
                return true;
            }
        }
        return false;
    }
}
