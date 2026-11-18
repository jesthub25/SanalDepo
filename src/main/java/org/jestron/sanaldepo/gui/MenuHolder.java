package org.jestron.sanaldepo.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * /depo menusu. Slot -> depo numarasi eslemesini tutar.
 */
public final class MenuHolder implements InventoryHolder {

    private final UUID owner;
    private final String ownerName;
    private final boolean admin;
    private final int[] slotToVault = new int[MenuLayout.SIZE];
    private Inventory inventory;

    public MenuHolder(UUID owner, String ownerName, boolean admin) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.admin = admin;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
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

    public boolean isAdmin() {
        return admin;
    }

    void map(int slot, int vault) {
        slotToVault[slot] = vault;
    }

    /** @return slottaki depo numarasi, yoksa 0 */
    public int vaultAt(int slot) {
        return slot >= 0 && slot < slotToVault.length ? slotToVault[slot] : 0;
    }
}
