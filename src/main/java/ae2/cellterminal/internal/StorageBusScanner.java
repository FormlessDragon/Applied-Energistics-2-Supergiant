package ae2.cellterminal.internal;

import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalScanner;
import ae2.api.networking.IGrid;
import ae2.core.AppEng;
import ae2.parts.storagebus.StorageBusPart;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.ResourceLocation;
import org.jspecify.annotations.NonNull;

import java.util.IdentityHashMap;
import java.util.List;

public final class StorageBusScanner implements CellTerminalScanner.Bus {
    private static final ResourceLocation ID = AppEng.makeId("cell_terminal/storage_bus_scanner");

    static List<StorageBusPart> storageBuses(IGrid grid) {
        var seen = new IdentityHashMap<StorageBusPart, Boolean>();
        var result = new ObjectArrayList<StorageBusPart>();
        for (var node : grid.getNodes()) {
            if (node.getOwner() instanceof StorageBusPart storageBus && seen.put(storageBus, Boolean.TRUE) == null) {
                result.add(storageBus);
            }
        }
        return result;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public @NonNull List<? extends CellTerminalBusTarget> scan(IGrid grid) {
        var result = new ObjectArrayList<CellTerminalBusTarget>();
        for (var storageBus : storageBuses(grid)) {
            if (storageBus.getSide() != null) {
                result.add(NativeCellTerminalTargets.createStorageBusTarget(storageBus));
            }
        }
        return List.copyOf(result);
    }
}
