package org.jestron.sanaldepo.gui;

/**
 * 54 slotluk menu duzeni: kenarlar dekor, ic 7x4 = 28 slot depo simgeleri.
 */
public final class MenuLayout {

    public static final int SIZE = 54;
    public static final int ROWS = 6;
    public static final int COLUMNS = 9;

    /** Depo simgelerinin siralandigi ic slotlar (soldan saga, yukaridan asagi). */
    public static final int[] INNER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    /** Bir oyuncunun sahip olabilecegi mutlak en fazla depo sayisi. */
    public static final int HARD_CAP = INNER_SLOTS.length;

    private MenuLayout() {
    }

    public static boolean isBorder(int slot) {
        int row = slot / COLUMNS;
        int col = slot % COLUMNS;
        return row == 0 || row == ROWS - 1 || col == 0 || col == COLUMNS - 1;
    }

    public static boolean isCorner(int slot) {
        return slot == 0 || slot == COLUMNS - 1 || slot == SIZE - COLUMNS || slot == SIZE - 1;
    }

    public static boolean isAlternate(int slot) {
        int row = slot / COLUMNS;
        int col = slot % COLUMNS;
        return ((row + col) & 1) == 1;
    }
}
