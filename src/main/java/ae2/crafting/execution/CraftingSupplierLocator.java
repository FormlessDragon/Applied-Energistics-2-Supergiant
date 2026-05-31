package ae2.crafting.execution;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class CraftingSupplierLocator {

    private CraftingSupplierLocator() {
    }

    public static List<CraftingSupplierLocation> collectMatchingProviderLocations(
        AEKey target,
        Iterable<IPatternDetails> patterns,
        Function<IPatternDetails, Iterable<ICraftingProvider>> providersForPattern,
        Function<ICraftingProvider, @Nullable CraftingSupplierLocation> locationResolver
    ) {
        Set<CraftingSupplierLocation> locations = new ObjectLinkedOpenHashSet<>();
        for (var pattern : patterns) {
            if (!patternProducesTarget(pattern, target)) {
                continue;
            }

            for (var provider : providersForPattern.apply(pattern)) {
                var location = locationResolver.apply(provider);
                if (location != null) {
                    locations.add(location);
                }
            }
        }
        return new ObjectArrayList<>(locations);
    }

    public static List<CraftingSupplierLocation> collectMatchingProviderLocations(IGrid grid, AEKey target,
                                                                                  Iterable<IPatternDetails> patterns,
                                                                                  Function<IPatternDetails, Iterable<ICraftingProvider>> providersForPattern) {
        return collectMatchingProviderLocations(target, patterns, providersForPattern,
            provider -> resolveLocation(grid, provider));
    }

    public static boolean patternProducesTarget(IPatternDetails pattern, AEKey target) {
        for (var output : pattern.getOutputs()) {
            if (target.matches(output)) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable CraftingSupplierLocation resolveLocation(IGrid grid, ICraftingProvider provider) {
        var node = resolveNode(grid, provider);
        if (node == null) {
            return null;
        }

        BlockPos pos = resolveBlockPos(node.getOwner());
        if (pos == null) {
            return null;
        }

        return new CraftingSupplierLocation(node.getLevel().provider.getDimension(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static @Nullable IGridNode resolveNode(IGrid grid, ICraftingProvider provider) {
        for (var node : grid.getNodes()) {
            if (node.getService(ICraftingProvider.class) == provider) {
                return node;
            }
        }
        return null;
    }

    private static @Nullable BlockPos resolveBlockPos(Object owner) {
        if (owner instanceof TileEntity tile) {
            return tile.getPos();
        }
        if (owner instanceof ae2.api.parts.IPartHost partHost) {
            return partHost.getLocation().getPos();
        }
        if (owner instanceof ae2.api.parts.IPart part && part instanceof ae2.parts.AEBasePart basePart
            && basePart.getHost() != null) {
            return basePart.getHost().getLocation().getPos();
        }
        if (owner instanceof Entity entity) {
            return entity.getPosition();
        }
        return null;
    }

    public static String getDimensionName(int dimensionId) {
        var world = DimensionManager.getWorld(dimensionId);
        if (world != null) {
            return world.provider.getDimensionType().getName();
        }

        var providerType = DimensionManager.getProviderType(dimensionId);
        return providerType != null ? providerType.getName() : Integer.toString(dimensionId);
    }
}
