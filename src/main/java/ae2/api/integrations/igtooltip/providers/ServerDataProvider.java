package ae2.api.integrations.igtooltip.providers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
@ApiStatus.OverrideOnly
@FunctionalInterface
public interface ServerDataProvider<T> {
    void provideServerData(EntityPlayer player, T object, NBTTagCompound serverData);
}
