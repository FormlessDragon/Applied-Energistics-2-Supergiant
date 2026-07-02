package ae2.parts.automation.special;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

public interface ModFilterHost {
    String MEMORY_CARD_MOD_ID = "modid";

    String getModFilter();

    void setModFilter(String expression);

    default void exportModFilterMemoryCardSettings(NBTTagCompound output) {
        output.setString(MEMORY_CARD_MOD_ID, getModFilter());
    }

    default void importModFilterMemoryCardSettings(NBTTagCompound input) {
        if (input.hasKey(MEMORY_CARD_MOD_ID, Constants.NBT.TAG_STRING)) {
            setModFilter(input.getString(MEMORY_CARD_MOD_ID));
        }
    }
}
