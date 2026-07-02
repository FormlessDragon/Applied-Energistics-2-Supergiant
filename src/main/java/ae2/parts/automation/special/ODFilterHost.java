package ae2.parts.automation.special;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

public interface ODFilterHost {
    String MEMORY_CARD_OD_WHITE = "odWhite";
    String MEMORY_CARD_OD_BLACK = "odBlack";

    String getODFilter(boolean whitelist);

    void setODFilter(String expression, boolean whitelist);

    default void exportODFilterMemoryCardSettings(NBTTagCompound output) {
        output.setString(MEMORY_CARD_OD_WHITE, getODFilter(true));
        output.setString(MEMORY_CARD_OD_BLACK, getODFilter(false));
    }

    default void importODFilterMemoryCardSettings(NBTTagCompound input) {
        if (input.hasKey(MEMORY_CARD_OD_WHITE, Constants.NBT.TAG_STRING)) {
            setODFilter(input.getString(MEMORY_CARD_OD_WHITE), true);
        }
        if (input.hasKey(MEMORY_CARD_OD_BLACK, Constants.NBT.TAG_STRING)) {
            setODFilter(input.getString(MEMORY_CARD_OD_BLACK), false);
        }
    }
}
