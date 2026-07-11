package ae2.api.networking.extensions;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry for attaching independent behavior to AE2 grid logic instances.
 */
public final class GridLogicExtensions {
    private static final Reference2ObjectMap<Item, Map<ResourceLocation, GridLogicExtensionFactory>> FACTORIES =
        new Reference2ObjectOpenHashMap<>();
    private static final Set<Item> FROZEN_REGISTRATIONS = java.util.Collections.newSetFromMap(
        new java.util.IdentityHashMap<>());

    private GridLogicExtensions() {
    }

    /**
     * Registers an extension factory for a machine item. Registration must happen before instances of that machine are
     * created. The id must be unique for the given machine.
     */
    public static synchronized void register(Item machineType, ResourceLocation registrationId,
                                             GridLogicExtensionFactory factory) {
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(factory, "factory");
        if (FROZEN_REGISTRATIONS.contains(machineType)) {
            throw new IllegalStateException("Grid logic extension registrations are already frozen for " + machineType);
        }

        var registrations = FACTORIES.computeIfAbsent(machineType, ignored -> new LinkedHashMap<>());
        var previous = registrations.putIfAbsent(registrationId, factory);
        if (previous != null) {
            throw new IllegalStateException("Grid logic extension " + registrationId
                + " is already registered for " + machineType);
        }
    }

    /**
     * Creates the extensions registered for a logic context. Custom grid logic implementations can call this to opt in
     * to the same extension mechanism.
     */
    public static List<GridLogicExtension> create(GridLogicContext context) {
        Objects.requireNonNull(context, "context");
        List<GridLogicExtensionFactory> factories;
        synchronized (GridLogicExtensions.class) {
            FROZEN_REGISTRATIONS.add(context.getMachineType());
            var registrations = FACTORIES.get(context.getMachineType());
            factories = registrations == null ? List.of() : List.copyOf(registrations.values());
        }

        var extensions = new ArrayList<GridLogicExtension>(factories.size());
        for (var factory : factories) {
            extensions.add(Objects.requireNonNull(factory.create(context), "extension factory returned null"));
        }
        return List.copyOf(extensions);
    }

    /**
     * Initializes a complete extension set after the owning logic has stored it.
     */
    public static void initialize(List<GridLogicExtension> extensions, GridLogicContext context) {
        Objects.requireNonNull(extensions, "extensions");
        Objects.requireNonNull(context, "context");
        for (var extension : extensions) {
            extension.initialize(context);
        }
    }

    /**
     * Returns the side of an immediately adjacent position, or {@code null} if the positions are not adjacent.
     */
    @Nullable
    public static EnumFacing getNeighborSide(BlockPos origin, BlockPos neighbor) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(neighbor, "neighbor");
        for (var side : EnumFacing.VALUES) {
            if (origin.offset(side).equals(neighbor)) {
                return side;
            }
        }
        return null;
    }
}
