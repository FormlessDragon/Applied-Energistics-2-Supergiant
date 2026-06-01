package ae2.api.parts;

import ae2.api.AECapabilities;
import ae2.api.behaviors.GenericInternalInventory;
import ae2.api.behaviors.GenericInternalInventoryAdapters;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.eventhandler.Event;

import javax.annotation.Nullable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class RegisterPartCapabilitiesEvent extends Event {

    final ObjectSet<Class<? extends TileEntity>> hostTypes = new ObjectOpenHashSet<>();

    final Object2ObjectMap<Capability<?>, PartCapabilityRegistration<?>> capabilityRegistrations = new Object2ObjectOpenHashMap<>();

    @SuppressWarnings("unchecked")
    public <T, P extends IPart> void register(Capability<T> capability,
                                              PartCapabilityProvider<P, T> provider,
                                              Class<P> partClass) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(partClass, "partClass");
        Objects.requireNonNull(provider, "provider");

        if (partClass.isInterface() || Modifier.isAbstract(partClass.getModifiers())) {
            throw new IllegalArgumentException(
                "Capabilities can only be registered for concrete part classes: " + partClass.getCanonicalName());
        }

        var registrations = (PartCapabilityRegistration<T>) capabilityRegistrations
            .computeIfAbsent(capability, ignored -> new PartCapabilityRegistration<>(capability));
        registrations.add(partClass, provider);
    }

    public <T extends TileEntity & IPartHost> void addHostType(Class<T> hostType) {
        hostTypes.add(hostType);
    }

    public void registerGenericInternalInventoryAdapters() {
        @SuppressWarnings("unchecked")
        var genericRegistration = (PartCapabilityRegistration<GenericInternalInventory>) capabilityRegistrations
            .get(AECapabilities.GENERIC_INTERNAL_INV);
        if (genericRegistration == null) {
            return;
        }

        var genericParts = new ArrayList<>(genericRegistration.parts.entrySet());
        for (var adapterEntry : GenericInternalInventoryAdapters.getAdapters().entrySet()) {
            Capability<?> capability = adapterEntry.getKey();
            registerGenericInternalInventoryAdapter(capability, genericParts);
        }
    }

    private <T> void registerGenericInternalInventoryAdapter(Capability<T> capability,
                                                             Iterable<Map.Entry<Class<? extends IPart>,
                                                                 PartCapabilityProvider<?, GenericInternalInventory>>> genericParts) {
        for (var genericPart : genericParts) {
            registerGenericInternalInventoryAdapter(capability, genericPart.getKey(), genericPart.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private <T, P extends IPart> void registerGenericInternalInventoryAdapter(Capability<T> capability,
                                                                              Class<? extends IPart> partClass,
                                                                              PartCapabilityProvider<?, GenericInternalInventory> genericProvider) {
        @SuppressWarnings("unchecked")
        var existingRegistration = (PartCapabilityRegistration<T>) capabilityRegistrations.get(capability);
        if (existingRegistration != null && existingRegistration.parts.containsKey(partClass)) {
            return;
        }

        register(capability, (part, side) -> {
            GenericInternalInventory genericInv = ((PartCapabilityProvider<P, GenericInternalInventory>) genericProvider)
                .getCapability(part, side);
            return genericInv == null ? null : GenericInternalInventoryAdapters.getCapability(genericInv, capability);
        }, (Class<P>) partClass);
    }

    @FunctionalInterface
    public interface PartCapabilityProvider<P extends IPart, T> {
        @Nullable
        T getCapability(P part, @Nullable EnumFacing side);
    }

    static final class PartCapabilityRegistration<T> {
        private final Capability<T> capability;
        private final Object2ObjectOpenHashMap<Class<? extends IPart>, PartCapabilityProvider<?, T>> parts = new Object2ObjectOpenHashMap<>();

        PartCapabilityRegistration(Capability<T> capability) {
            this.capability = capability;
        }

        <P extends IPart> void add(Class<P> partClass, PartCapabilityProvider<P, T> provider) {
            if (parts.putIfAbsent(partClass, provider) != null) {
                throw new IllegalStateException("Cannot register an additional capability provider for part "
                    + partClass + " since there already is one for capability " + capability);
            }
        }

        @Nullable
        public T getCapability(IPartHost host, @Nullable EnumFacing side) {
            var part = host.getPart(side);
            if (part != null) {
                return handlePart(part, side);
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        @Nullable
        private <P extends IPart> T handlePart(P part, @Nullable EnumFacing side) {
            Class<?> currentClass = part.getClass();
            while (currentClass != null && IPart.class.isAssignableFrom(currentClass)) {
                var partProvider = (PartCapabilityProvider<P, T>) parts.get(currentClass);
                if (partProvider != null) {
                    return partProvider.getCapability(part, side);
                }
                currentClass = currentClass.getSuperclass();
            }
            return null;
        }
    }
}
