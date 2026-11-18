package org.jestron.sanaldepo.vault;

import org.bukkit.inventory.ItemStack;
import org.jestron.sanaldepo.gui.VaultHolder;
import org.jestron.sanaldepo.storage.ItemCodec;

/**
 * Tek bir deponun bellekteki durumu. Yalnizca ana is parcacigindan erisilir.
 */
public final class VaultSlot {

    public static final int SIZE = 54;

    final int number;

    /** Depo kapaliyken icerik. Depo aciksa asil kaynak {@link #holder} envanteridir. */
    ItemStack[] contents = new ItemStack[SIZE];

    /** Veritabanina en son gonderilen veri (null = satir yok / bos). */
    byte[] lastSaved;

    /** Veri cozulemedi: acilmaz ve ASLA uzerine yazilmaz (veri kaybi korumasi). */
    boolean corrupt;

    /** Son kayit basarisiz oldu, bir sonraki firsatta zorla tekrar kaydedilir. */
    boolean saveFailed;

    /** Sirali yazma numarasi (eski bir yazmanin sonucunun yenisini ezmemesi icin). */
    int writeSeq;

    /** Acik canli envanter (yoksa null). Ayni depoyu acan herkes AYNI envanteri gorur -> dupe imkansiz. */
    VaultHolder holder;

    VaultSlot(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public boolean isCorrupt() {
        return corrupt;
    }

    public VaultHolder getHolder() {
        return holder;
    }

    /** Su anki gercek icerik (aciksa canli envanter). */
    public ItemStack[] currentContents() {
        return holder != null ? holder.getInventory().getContents() : contents;
    }

    public int usedSlots() {
        int used = 0;
        for (ItemStack it : currentContents()) {
            if (!ItemCodec.isAir(it)) {
                used++;
            }
        }
        return used;
    }
}
