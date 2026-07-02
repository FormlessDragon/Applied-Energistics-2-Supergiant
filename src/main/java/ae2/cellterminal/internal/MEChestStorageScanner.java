package ae2.cellterminal.internal;

import ae2.api.cellterminal.CellTerminalScanner;
import ae2.api.cellterminal.CellTerminalStorageTarget;
import ae2.api.networking.IGrid;
import ae2.core.AppEng;
import ae2.tile.storage.TileMEChest;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.ResourceLocation;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class MEChestStorageScanner implements CellTerminalScanner.Storage {
    private static final ResourceLocation ID = AppEng.makeId("cell_terminal/me_chest_storage_scanner");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public @NonNull List<? extends CellTerminalStorageTarget> scan(IGrid grid) {
        var machines = grid.getMachines(TileMEChest.class);
        var result = new ObjectArrayList<CellTerminalStorageTarget>(machines.size());
        for (var chest : machines) {
            result.add(NativeCellTerminalTargets.createMEChestStorageTarget(chest));
        }
        return List.copyOf(result);
    }
}
