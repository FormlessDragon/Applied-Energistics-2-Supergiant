package ae2.integration.modules.theoneprobe;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.parts.IPartHost;
import ae2.api.parts.SelectedPart;
import ae2.me.Grid;
import ae2.me.InWorldGridNode;
import ae2.me.helpers.IGridConnectedTile;
import ae2.me.service.TickManagerService;
import ae2.parts.networking.CablePart;
import ae2.parts.p2p.P2PTunnelPart;
import ae2.parts.storagebus.StorageBusPart;
import ae2.tile.crafting.ICraftingCPUTileEntity;
import ae2.tile.crafting.TileMolecularAssembler;
import ae2.tile.crafting.TilePatternProvider;
import ae2.tile.networking.TileCableBus;
import ae2.tile.qnb.TileQuantumBridge;
import ae2.tile.storage.TileDrive;
import ae2.tile.storage.TileIOPort;
import ae2.tile.storage.TileMEChest;
import ae2.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

public final class TopNetworkDebugProvider {
    private TopNetworkDebugProvider() {
    }

    public static void addProbeInfo(EntityPlayer player, TileEntity blockEntity, Vec3d hitLocation,
                                    TopTooltipBuilder tooltipBuilder) {
        if (blockEntity instanceof TileQuantumBridge quantumBridge) {
            addQuantumBridgeInfo(quantumBridge, tooltipBuilder);
        }

        if (!player.isSneaking() || !TickManagerService.MONITORING_ENABLED) {
            return;
        }

        var context = resolveContext(blockEntity, hitLocation);
        if (context == null) {
            return;
        }

        var grid = context.node().grid();
        if (!(grid instanceof Grid concreteGrid)) {
            return;
        }

        var snapshot = buildSnapshot(concreteGrid);
        if (snapshot.pivotPos() != null) {
            tooltipBuilder.addLine(label(TopText.netdebug_grid_pivot_pos)
                + axis("X", snapshot.pivotPos().getX(), TextFormatting.RED)
                + " " + axis("Y", snapshot.pivotPos().getY(), TextFormatting.GREEN)
                + " " + axis("Z", snapshot.pivotPos().getZ(), TextFormatting.AQUA)
                + (context.isPivot() ? TextFormatting.GRAY + " [C]" : ""));
        }
        tooltipBuilder.addLine(labeledValue(TopText.netdebug_grid_id, Integer.toString(snapshot.gridId())));
        tooltipBuilder.addLine(labeledValue(TopText.netdebug_grid_nodes, Integer.toString(snapshot.gridNodes())));
        tooltipBuilder.addLine(label(TopText.netdebug_grid_cpu_avg_max)
            + value(snapshot.cpuAverage(), TextFormatting.GREEN)
            + " / "
            + value(snapshot.cpuMax(), TextFormatting.AQUA));
        tooltipBuilder.addLine(labeledValue(TopText.netdebug_storage, snapshot.storage()));
        tooltipBuilder.addLine(labeledValue(TopText.netdebug_crafting, snapshot.crafting()));
        tooltipBuilder.addLine(labeledValue(TopText.netdebug_tick, snapshot.tick()));
        tooltipBuilder.addLine(labeledValue(TopText.netdebug_misc, snapshot.misc()));
    }

    private static void addQuantumBridgeInfo(TileQuantumBridge quantumBridge, TopTooltipBuilder tooltipBuilder) {
        var cluster = quantumBridge.getCluster();
        if (cluster == null) {
            tooltipBuilder.addLine(value(local(TopText.quantum_link_missing), TextFormatting.RED));
            return;
        }

        TileQuantumBridge linkedCenter = cluster.getLinkedCenter();
        if (linkedCenter == null || linkedCenter.getWorld() == null) {
            tooltipBuilder.addLine(value(local(TopText.quantum_link_missing), TextFormatting.RED));
            return;
        }

        String dimensionName = linkedCenter.getWorld().provider.getDimensionType().getName();
        int dimensionId = linkedCenter.getWorld().provider.getDimension();
        tooltipBuilder.addLine(label(TopText.quantum_link_dimension)
            + value(dimensionName, TextFormatting.GREEN)
            + " " + value("(" + dimensionId + ")", TextFormatting.GREEN));

        BlockPos linkedPos = linkedCenter.getPos();
        tooltipBuilder.addLine(label(TopText.quantum_link_position)
            + axis("X", linkedPos.getX(), TextFormatting.RED)
            + " " + axis("Y", linkedPos.getY(), TextFormatting.GREEN)
            + " " + axis("Z", linkedPos.getZ(), TextFormatting.AQUA));
    }

    private static @Nullable ResolvedContext resolveContext(TileEntity blockEntity, Vec3d hitLocation) {
        if (blockEntity instanceof IPartHost partHost) {
            SelectedPart selected = partHost.selectPartWorld(hitLocation);
            if (selected.part != null) {
                IGridNode node = selected.part.getGridNode();
                if (node != null) {
                    return new ResolvedContext(node, isPivot(node.grid(), node));
                }
            }
        }

        if (blockEntity instanceof IGridConnectedTile gridConnectedTile) {
            var node = gridConnectedTile.getActionableNode();
            if (node != null) {
                return new ResolvedContext(node, isPivot(node.grid(), node));
            }
        }

        return null;
    }

    private static boolean isPivot(IGrid grid, IGridNode node) {
        return grid.getPivot() == node;
    }

    private static Snapshot buildSnapshot(Grid grid) {
        var tickManager = (TickManagerService) grid.getTickManager();
        long cpuAverage = 0;
        long cpuMax = 0;
        long storage = 0;
        long crafting = 0;
        long tick = 0;
        long misc = 0;

        for (var node : grid.getNodes()) {
            if (node.getService(IGridTickable.class) == null) {
                continue;
            }

            long avg = tickManager.getAverageTime(node);
            long max = tickManager.getMaximumTime(node);
            cpuAverage += avg;
            cpuMax = Math.max(cpuMax, max);

            switch (classify(node.getOwner())) {
                case STORAGE -> storage += avg;
                case CRAFTING -> crafting += avg;
                case TICK -> tick += avg;
                case MISC -> misc += avg;
            }
        }

        BlockPos pivotPos = null;
        var pivot = grid.getPivot();
        if (pivot instanceof InWorldGridNode inWorldGridNode) {
            pivotPos = inWorldGridNode.getLocation();
        }

        return new Snapshot(
            grid.getSerialNumber(),
            grid.size(),
            pivotPos,
            Platform.formatTimeMeasurement(cpuAverage),
            Platform.formatTimeMeasurement(cpuMax),
            Platform.formatTimeMeasurement(storage),
            Platform.formatTimeMeasurement(crafting),
            Platform.formatTimeMeasurement(tick),
            Platform.formatTimeMeasurement(misc));
    }

    private static Category classify(Object owner) {
        if (owner instanceof StorageBusPart
            || owner instanceof TileDrive
            || owner instanceof TileMEChest
            || owner instanceof TileIOPort) {
            return Category.STORAGE;
        }

        if (owner instanceof TileMolecularAssembler
            || owner instanceof TilePatternProvider
            || owner instanceof ICraftingCPUTileEntity) {
            return Category.CRAFTING;
        }

        if (owner instanceof CablePart
            || owner instanceof P2PTunnelPart
            || owner instanceof TileCableBus) {
            return Category.TICK;
        }

        return Category.MISC;
    }

    private static String labeledValue(TopText label, String value) {
        return label(label) + value(value, TextFormatting.GREEN);
    }

    private static String axis(String axis, int value, TextFormatting color) {
        return axis + ":" + value(Integer.toString(value), color);
    }

    private static String label(TopText label) {
        return local(label) + ": ";
    }

    private static String value(String value, TextFormatting color) {
        return color + value + TextFormatting.WHITE;
    }

    private static String local(TopText text) {
        return TopTooltipFormatter.localize(text);
    }

    private enum Category {
        STORAGE,
        CRAFTING,
        TICK,
        MISC
    }

    private record Snapshot(int gridId, int gridNodes, @Nullable BlockPos pivotPos, String cpuAverage, String cpuMax,
                            String storage, String crafting, String tick, String misc) {
    }

    private record ResolvedContext(IGridNode node, boolean isPivot) {
    }
}
