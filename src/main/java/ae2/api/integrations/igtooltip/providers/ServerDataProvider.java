package ae2.api.integrations.igtooltip.providers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

@FunctionalInterface
public interface ServerDataProvider<T> {
    void provideServerData(EntityPlayer player, T object, NBTTagCompound serverData);
}
