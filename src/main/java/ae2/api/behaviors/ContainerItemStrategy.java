package ae2.api.behaviors;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Strategy to interact with the non-item keys held by container items, for example the fluid contained in a bucket.
 *
 * @param <C> Any context object that can accept or offer resources, directly or indirectly. Usually a platform
 *            storage handler or container interaction carrier.
 */
@ApiStatus.Experimental
public interface ContainerItemStrategy<T extends AEKey, C> {
    static <T extends AEKey> void register(AEKeyType keyType, Class<T> keyClass, ContainerItemStrategy<T, ?> strategy) {
        ContainerItemStrategies.register(keyType, keyClass, strategy);
    }

    @Nullable
    GenericStack getContainedStack(ItemStack stack);

    @Nullable
    C findCarriedContext(EntityPlayer player, Container container);

    @Nullable
    default C findPlayerSlotContext(EntityPlayer player, int slot) {
        return null;
    }

    long extract(C context, T what, long amount, Actionable mode);

    long insert(C context, T what, long amount, Actionable mode);

    void playFillSound(EntityPlayer player, T what);

    void playEmptySound(EntityPlayer player, T what);

    @Nullable
    GenericStack getExtractableContent(C context);

}
