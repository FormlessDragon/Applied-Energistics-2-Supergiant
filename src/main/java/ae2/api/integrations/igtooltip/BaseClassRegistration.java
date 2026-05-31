package ae2.api.integrations.igtooltip;

import ae2.api.parts.IPartHost;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.ApiStatus;

/**
 * Allows add-ons to notify AE2 of their {@link TileEntity} classes that derive from AE2 tile entity classes. AE2
 * will try to add default tooltip providers for common AE2 API interfaces for these addon tile entity classes.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface BaseClassRegistration {
    /**
     * Adds AE2s tooltip providers for the following interfaces to a given tile entity/block and their subclasses.
     * <ul>
     * <li>{@link ae2.api.networking.energy.IAEPowerStorage}</li>
     * </ul>
     * <p/>
     * Please note that AE2 will already register these providers for its own tile entity base class. This method is
     * only useful if you implement any of the interfaces listed above on your own tile entity class, which does not
     * extend from an internal AE2 tile entity base class.
     * <p/>
     * This method is needed because some tooltip mods only allow registering providers for subclasses of
     * {@link TileEntity}, and not for arbitrary interfaces.
     *
     * @see TooltipProvider#registerBlockEntityBaseClasses
     */
    void addBaseBlockEntity(Class<? extends TileEntity> blockEntityClass,
                            Class<? extends Block> blockClass);

    /**
     * Adds AE2s part tooltip providers for third party {@link IPartHost} implementations.
     * <p/>
     * Please note that AE2 will already register these providers for its own part host (`TileCableBus`). This
     * method is only useful if your addon implements its own {@link IPartHost}, which does not extend from an internal
     * AE2 tile entity base class.
     * <p/>
     * This method is needed because some tooltip mods only allow registering providers for subclasses of
     * {@link TileEntity}, and not for arbitrary interfaces.
     *
     * @see TooltipProvider#registerBlockEntityBaseClasses
     */
    <T extends TileEntity & IPartHost> void addPartHost(Class<T> blockEntityClass,
                                                        Class<? extends Block> blockClass);
}
