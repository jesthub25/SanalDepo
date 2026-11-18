package org.jestron.sanaldepo.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Acik bir deponun canli envanteri. Ayni depo icin tek bir ornek vardir;
 * sahibi ve yoneticiler ayni envanteri gorur.
 */
public final class VaultHolder implements InventoryHolder {

    private final UUID owner;
    private final String ownerName;
    private final int number;
    private final Inventory inventory;
    private final Set<UUID> readOnlyViewers = new HashSet<>();

    public VaultHolder(UUID owner, String ownerName, int number, int size, String title) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.number = number;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getNumber() {
        return number;
    }

    public boolean isReadOnly(UUID viewer) {
        return readOnlyViewers.contains(viewer);
    }

    public void setReadOnly(UUID viewer, boolean readOnly) {
        if (readOnly) {
            readOnlyViewers.add(viewer);
        } else {
            readOnlyViewers.remove(viewer);
        }
    }
}
