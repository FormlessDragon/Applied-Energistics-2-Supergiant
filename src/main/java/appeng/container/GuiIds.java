package appeng.container;

import org.jetbrains.annotations.NotNull;

public final class GuiIds {

    public static final int CONTROLLER_STATUS = GuiKey.CONTROLLER_STATUS.getGuiId();
    public static final int ME_CHEST = GuiKey.ME_CHEST.getGuiId();
    private static final int RETURNED_FROM_SUBSCREEN_FLAG = 1 << 30;

    private GuiIds() {
    }

    public static int getGuiId(GuiKey key, boolean returnedFromSubScreen) {
        return returnedFromSubScreen ? key.getGuiId() | RETURNED_FROM_SUBSCREEN_FLAG : key.getGuiId();
    }

    public static boolean isReturnedFromSubScreen(int guiId) {
        return (guiId & RETURNED_FROM_SUBSCREEN_FLAG) != 0;
    }

    private static int getBaseGuiId(int guiId) {
        return guiId & ~RETURNED_FROM_SUBSCREEN_FLAG;
    }

    public enum GuiKey {
        CONTROLLER_STATUS,
        ME_CHEST,
        DRIVE,
        CELL_WORKBENCH,
        CONDENSER,
        SKY_CHEST,
        INSCRIBER,
        IO_PORT,
        MOLECULAR_ASSEMBLER,
        VIBRATION_CHAMBER,
        QNB,
        WIRELESS_ACCESS_POINT,
        SPATIAL_IO_PORT,
        SPATIAL_ANCHOR,
        QUARTZ_KNIFE,
        NETWORK_TOOL,
        NETWORK_STATUS,
        INTERFACE,
        PATTERN_PROVIDER,
        CRAFTING_CPU,
        IMPORT_BUS,
        EXPORT_BUS,
        STORAGE_BUS,
        FORMATION_PLANE,
        ENERGY_LEVEL_EMITTER,
        STORAGE_LEVEL_EMITTER,
        ME_STORAGE_TERMINAL,
        CRAFTING_TERMINAL,
        PATTERN_ENCODING_TERMINAL,
        PATTERN_ACCESS_TERMINAL,
        PORTABLE_ITEM_CELL,
        PORTABLE_FLUID_CELL,
        WIRELESS_TERMINAL,
        WIRELESS_CRAFTING_TERMINAL,
        WIRELESS_PATTERN_ENCODING_TERMINAL,
        WIRELESS_PATTERN_ACCESS_TERMINAL,
        WIRELESS_UNIVERSAL_TERMINAL_SELECTOR,
        WIRELESS_MAGNET,
        CRAFT_AMOUNT,
        CRAFT_CONFIRM,
        CRAFTING_STATUS,
        SET_STOCK_AMOUNT,
        PRIORITY,
        BASIC_CELL_CHEST,
        CRAFTING_TREE;

        public static @NotNull GuiKey fromId(int guiId) {
            int baseGuiId = getBaseGuiId(guiId);
            return values()[baseGuiId];
        }

        public int getGuiId() {
            return this.ordinal();
        }
    }
}
