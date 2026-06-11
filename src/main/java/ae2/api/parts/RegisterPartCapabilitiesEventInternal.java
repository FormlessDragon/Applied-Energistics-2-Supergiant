package ae2.api.parts;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.Nullable;

public final class RegisterPartCapabilitiesEventInternal {
    private static final ObjectOpenHashSet<Class<? extends TileEntity>> HOST_TYPES = new ObjectOpenHashSet<>();
    private static final Object2ObjectOpenHashMap<Capability<?>, RegisterPartCapabilitiesEvent.PartCapabilityRegistration<?>> REGISTRATIONS = new Object2ObjectOpenHashMap<>();

    private RegisterPartCapabilitiesEventInternal() {
    }

    public static synchronized void register(RegisterPartCapabilitiesEvent partEvent) {
        HOST_TYPES.addAll(partEvent.hostTypes);
        REGISTRATIONS.putAll(partEvent.capabilityRegistrations);
    }

    public static synchronized boolean hasCapability(IPartHost host, Capability<?> capability, @Nullable EnumFacing side) {
        return getCapability(host, capability, side) != null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static synchronized <T> T getCapability(IPartHost host, Capability<T> capability, @Nullable EnumFacing side) {
        if (!(host instanceof TileEntity tileEntity) || !supportsHost(tileEntity)) {
            return null;
        }

        var registration = (RegisterPartCapabilitiesEvent.PartCapabilityRegistration<T>) REGISTRATIONS.get(capability);
        if (registration == null) {
            return null;
        }

        return registration.getCapability(host, side);
    }

    private static boolean supportsHost(TileEntity tileEntity) {
        for (var hostType : HOST_TYPES) {
            if (hostType.isInstance(tileEntity)) {
                return true;
            }
        }
        return false;
    }
}
