package ae2.client.gui.me.requester;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

public interface RequesterDisplay {
    void postClearAll();

    void postFullUpdate(long requesterId, @Nullable ITextComponent requesterName, long sortValue, int requestCount,
                        Int2ObjectMap<NBTTagCompound> rows);

    void postIncrementalUpdate(long requesterId, Int2ObjectMap<NBTTagCompound> rows);
}
