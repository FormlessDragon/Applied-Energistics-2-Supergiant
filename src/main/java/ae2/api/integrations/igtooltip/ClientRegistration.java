package ae2.api.integrations.igtooltip;

import ae2.api.integrations.igtooltip.providers.BodyProvider;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

public interface ClientRegistration {
    default <T extends TileEntity> void addBlockEntityBody(Class<T> blockEntityClass,
                                                           Class<? extends Block> blockClass,
                                                           ResourceLocation id,
                                                           BodyProvider<? super T> provider) {
        addBlockEntityBody(blockEntityClass, blockClass, id, provider, 1000);
    }

    <T extends TileEntity> void addBlockEntityBody(Class<T> blockEntityClass,
                                                   Class<? extends Block> blockClass,
                                                   ResourceLocation id,
                                                   BodyProvider<? super T> provider,
                                                   int priority);
}
