package ae2.api.behaviors;

import ae2.api.AECapabilities;
import ae2.util.CowMap;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Allows addons to expose AE2's generic internal inventories through Forge capabilities.
 */
@ApiStatus.Experimental
public final class GenericInternalInventoryAdapters {
    private static final CowMap<Capability<?>, Function<GenericInternalInventory, ?>> adapters = CowMap
        .identityHashMap();

    private GenericInternalInventoryAdapters() {
    }

    public static synchronized <T> void register(Capability<T> capability,
                                                 Function<GenericInternalInventory, T> adapter) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(adapter, "adapter");

        if (capability == AECapabilities.GENERIC_INTERNAL_INV) {
            throw new IllegalArgumentException("GENERIC_INTERNAL_INV cannot be adapted to itself");
        }

        if (!adapters.getMap().containsKey(capability)) {
            adapters.putIfAbsent(capability, adapter);
        }
    }

    public static synchronized boolean hasAdapter(Capability<?> capability) {
        return adapters.getMap().containsKey(capability);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static synchronized <T> T getCapability(GenericInternalInventory inv, Capability<T> capability) {
        var adapter = (Function<GenericInternalInventory, T>) adapters.getMap().get(capability);
        return adapter == null ? null : adapter.apply(inv);
    }

    public static synchronized Map<Capability<?>, Function<GenericInternalInventory, ?>> getAdapters() {
        return adapters.getMap();
    }
}
