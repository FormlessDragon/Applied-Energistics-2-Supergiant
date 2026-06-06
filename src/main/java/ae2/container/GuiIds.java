package ae2.container;

import org.jetbrains.annotations.NotNull;

public final class GuiIds {

    @SuppressWarnings("unused")
    public static final int CONTROLLER_STATUS = GuiKey.CONTROLLER_STATUS.getGuiId();
    @SuppressWarnings("unused")
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
        CRYSTAL_ASSEMBLER,
        INGREDIENT_BUFFER,
        CANER,
        IO_PORT,
        MOLECULAR_ASSEMBLER,
        VIBRATION_CHAMBER,
        QNB,
        WIRELESS_ACCESS_POINT,
        SPATIAL_IO_PORT,
        SPATIAL_ANCHOR,
        QUARTZ_KNIFE,
        NETWORK_TOOL,
        NETWORK_ANALYSER,
        TICK_ANALYSER,
        NETWORK_STATUS,
        INTERFACE,
        PATTERN_PROVIDER,
        REQUESTER,
        CRAFTING_CPU,
        IMPORT_BUS,
        EXPORT_BUS,
        STORAGE_BUS,
        OD_EXPORT_BUS,
        MOD_EXPORT_BUS,
        PRECISE_EXPORT_BUS,
        THRESHOLD_EXPORT_BUS,
        STOCK_EXPORT_BUS,
        IMPORT_EXPORT_BUS,
        ADVANCED_IO_BUS,
        OD_STORAGE_BUS,
        MOD_STORAGE_BUS,
        PRECISE_STORAGE_BUS,
        FORMATION_PLANE,
        ENERGY_LEVEL_EMITTER,
        STORAGE_LEVEL_EMITTER,
        ME_STORAGE_TERMINAL,
        CRAFTING_TERMINAL,
        PATTERN_ENCODING_TERMINAL,
        PATTERN_ACCESS_TERMINAL,
        REQUESTER_TERMINAL,
        PORTABLE_ITEM_CELL,
        PORTABLE_FLUID_CELL,
        WIRELESS_TERMINAL,
        WIRELESS_CRAFTING_TERMINAL,
        WIRELESS_PATTERN_ENCODING_TERMINAL,
        WIRELESS_PATTERN_ACCESS_TERMINAL,
        WIRELESS_REQUESTER_TERMINAL,
        WIRELESS_UNIVERSAL_TERMINAL_SELECTOR,
        WIRELESS_MAGNET,
        CRAFT_AMOUNT,
        CRAFT_CONFIRM,
        CRAFTING_STATUS,
        OUTPUT_SIDES,
        SET_STOCK_AMOUNT,
        PRIORITY,
        BASIC_CELL_CHEST,
        CRAFTING_TREE,
        VOID_CELL,
        ANNIHILATION_PLANE,
        THRESHOLD_LEVEL_EMITTER,
        CONFIG_MODIFIER,
        PATTERN_MODIFIER,
        RENAMER;

        public static @NotNull GuiKey fromId(int guiId) {
            int baseGuiId = getBaseGuiId(guiId);
            return values()[baseGuiId];
        }

        public int getGuiId() {
            return this.ordinal();
        }
    }
}
