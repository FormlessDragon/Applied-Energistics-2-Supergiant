package ae2.api.integrations.igtooltip;

import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.IconProvider;
import ae2.api.integrations.igtooltip.providers.ModNameProvider;
import ae2.api.integrations.igtooltip.providers.NameProvider;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
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

    default <T extends TileEntity> void addBlockEntityIcon(Class<T> blockEntityClass,
                                                           Class<? extends Block> blockClass,
                                                           ResourceLocation id,
                                                           IconProvider<? super T> provider) {
        addBlockEntityIcon(blockEntityClass, blockClass, id, provider, TooltipProvider.DEFAULT_PRIORITY);
    }

    <T extends TileEntity> void addBlockEntityIcon(Class<T> blockEntityClass,
                                                   Class<? extends Block> blockClass,
                                                   ResourceLocation id,
                                                   IconProvider<? super T> provider,
                                                   int priority);

    default <T extends TileEntity> void addBlockEntityName(Class<T> blockEntityClass,
                                                           Class<? extends Block> blockClass,
                                                           ResourceLocation id,
                                                           NameProvider<? super T> provider) {
        addBlockEntityName(blockEntityClass, blockClass, id, provider, TooltipProvider.DEFAULT_PRIORITY);
    }

    <T extends TileEntity> void addBlockEntityName(Class<T> blockEntityClass,
                                                   Class<? extends Block> blockClass,
                                                   ResourceLocation id, NameProvider<? super T> provider,
                                                   int priority);

    default <T extends TileEntity> void addBlockEntityModName(Class<T> blockEntityClass,
                                                              Class<? extends Block> blockClass,
                                                              ResourceLocation id,
                                                              ModNameProvider<? super T> provider) {
        addBlockEntityModName(blockEntityClass, blockClass, id, provider, 1000);
    }

    <T extends TileEntity> void addBlockEntityModName(Class<T> blockEntityClass,
                                                      Class<? extends Block> blockClass,
                                                      ResourceLocation id,
                                                      ModNameProvider<? super T> provider,
                                                      int priority);

}
