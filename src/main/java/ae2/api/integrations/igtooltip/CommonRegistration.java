package ae2.api.integrations.igtooltip;

import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface CommonRegistration {
    <T extends TileEntity> void addBlockEntityData(ResourceLocation id,
                                                   Class<T> blockEntityClass,
                                                   ServerDataProvider<? super T> provider);

}
