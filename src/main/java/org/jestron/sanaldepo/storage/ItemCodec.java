package org.jestron.sanaldepo.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Depo icerigini sikistirilmis byte dizisine cevirir ve geri okur.
 *
 * Bicim: [1 byte format] + GZIP(veri)
 *   format 1 = Bukkit nesne akisi (her sunucuda okunur)
 *   format 2 = Paper ItemStack#serializeAsBytes (veri surumu yukseltmelerinde en guvenli)
 *
 * Okuma her iki formati da destekler; yazma ayara ve sunucuya gore secilir.
 * Ana is parcaciginda (main thread) cagrilmalidir.
 */
public final class ItemCodec {

    private static final byte FORMAT_BUKKIT = 1;
    private static final byte FORMAT_PAPER = 2;
    private static final int MAX_SLOTS = 54;
    private static final int MAX_ITEM_BYTES = 8 * 1024 * 1024;

    private final MethodHandle serializeAsBytes;
    private final MethodHandle deserializeBytes;
    private final boolean writePaper;

    public ItemCodec(boolean preferPaper) {
        MethodHandle ser = null;
        MethodHandle de = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            ser = lookup.findVirtual(ItemStack.class, "serializeAsBytes", MethodType.methodType(byte[].class));
            de = lookup.findStatic(ItemStack.class, "deserializeBytes", MethodType.methodType(ItemStack.class, byte[].class));
        } catch (Throwable ignored) {
            ser = null;
            de = null;
        }
        this.serializeAsBytes = ser;
        this.deserializeBytes = de;
        this.writePaper = preferPaper && ser != null && de != null;
    }

    public boolean isPaperFormat() {
        return writePaper;
    }

    public static boolean isEmpty(ItemStack[] items) {
        if (items == null) {
            return true;
        }
        for (ItemStack it : items) {
            if (!isAir(it)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAir(ItemStack it) {
        return it == null || it.getType().isAir() || it.getAmount() <= 0;
    }

    public byte[] encode(ItemStack[] items) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(512);
        if (writePaper) {
            bos.write(FORMAT_PAPER);
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bos))) {
                out.writeInt(items.length);
                for (ItemStack it : items) {
                    if (isAir(it)) {
                        out.writeInt(0);
                        continue;
                    }
                    byte[] data;
                    try {
                        data = (byte[]) serializeAsBytes.invoke(it);
                    } catch (Throwable t) {
                        throw new IOException("Esya kodlanamadi: " + it.getType(), t);
                    }
                    out.writeInt(data.length);
                    out.write(data);
                }
            }
        } else {
            bos.write(FORMAT_BUKKIT);
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(new GZIPOutputStream(bos))) {
                out.writeInt(items.length);
                for (ItemStack it : items) {
                    out.writeObject(isAir(it) ? null : it);
                }
            }
        }
        return bos.toByteArray();
    }

    public ItemStack[] decode(byte[] data, int size) throws IOException {
        if (data == null || data.length < 2) {
            throw new IOException("Bos veya kisa veri");
        }
        byte format = data[0];
        InputStream raw = new GZIPInputStream(new ByteArrayInputStream(data, 1, data.length - 1));
        ItemStack[] result = new ItemStack[size];
        if (format == FORMAT_PAPER) {
            if (deserializeBytes == null) {
                raw.close();
                throw new IOException("Bu depo Paper formatinda kaydedilmis, bu sunucu Paper degil (esya-formati: BUKKIT ile tekrar kaydedin).");
            }
            try (DataInputStream in = new DataInputStream(raw)) {
                int len = in.readInt();
                checkLength(len);
                for (int i = 0; i < len; i++) {
                    int n = in.readInt();
                    if (n == 0) {
                        continue;
                    }
                    if (n < 0 || n > MAX_ITEM_BYTES) {
                        throw new IOException("Gecersiz esya boyutu: " + n);
                    }
                    byte[] buf = new byte[n];
                    in.readFully(buf);
                    ItemStack it;
                    try {
                        it = (ItemStack) deserializeBytes.invoke(buf);
                    } catch (Throwable t) {
                        throw new IOException("Esya cozulemedi (slot " + i + ")", t);
                    }
                    put(result, i, it);
                }
            }
        } else if (format == FORMAT_BUKKIT) {
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(raw)) {
                int len = in.readInt();
                checkLength(len);
                for (int i = 0; i < len; i++) {
                    Object o;
                    try {
                        o = in.readObject();
                    } catch (ClassNotFoundException e) {
                        throw new IOException("Esya cozulemedi (slot " + i + ")", e);
                    }
                    if (o != null && !(o instanceof ItemStack)) {
                        throw new IOException("Beklenmeyen nesne (slot " + i + "): " + o.getClass().getName());
                    }
                    put(result, i, (ItemStack) o);
                }
            }
        } else {
            raw.close();
            throw new IOException("Bilinmeyen veri formati: " + format);
        }
        return result;
    }

    private static void checkLength(int len) throws IOException {
        if (len < 0 || len > MAX_SLOTS) {
            throw new IOException("Gecersiz slot sayisi: " + len);
        }
    }

    private static void put(ItemStack[] arr, int index, ItemStack it) throws IOException {
        if (isAir(it)) {
            return;
        }
        if (index >= arr.length) {
            // Kayitli veri mevcut boyuttan buyukse esyayi kaybetmek yerine depoyu kilitle
            throw new IOException("Kayitli esya depo boyutunun disinda (slot " + index + ")");
        }
        arr[index] = it;
    }
}
