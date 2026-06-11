package ae2.api.behaviors;

import ae2.api.stacks.AEKeyType;
import ae2.core.AEConfig;
import ae2.util.CowReference2LongMap;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;

/**
 * Allows custom key types to define slot capacities for pattern providers and interfaces.
 */
public class GenericSlotCapacities {
    private static final CowReference2LongMap<AEKeyType> map = CowReference2LongMap.newMap();

    static {
        register(AEKeyType.items(), AEConfig.instance().getInterfaceItemSlotCapacity());
        register(AEKeyType.fluids(), AEConfig.instance().getInterfaceFluidSlotCapacity());
    }

    private GenericSlotCapacities() {
    }

    public static void register(AEKeyType type, long capacity) {
        Preconditions.checkArgument(capacity >= 0, "capacity >= 0");
        map.putIfAbsent(type, capacity);
    }

    public static void modifyValue(AEKeyType key, long value) {
        try {
            map.modifyValue(key, value);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unregistered AEKeyType");
        }
    }

    public static Reference2LongMap<AEKeyType> getMap() {
        return map.getMap();
    }
}
