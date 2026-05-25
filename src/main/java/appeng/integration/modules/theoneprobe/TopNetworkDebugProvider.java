package appeng.integration.modules.theoneprobe;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.me.Grid;
import appeng.me.InWorldGridNode;
import appeng.me.helpers.IGridConnectedTile;
import appeng.me.service.TickManagerService;
import appeng.parts.networking.CablePart;
import appeng.parts.p2p.P2PTunnelPart;
import appeng.parts.storagebus.StorageBusPart;
import appeng.tile.crafting.TileCraftingMonitor;
import appeng.tile.crafting.TileCraftingUnit;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.crafting.TilePatternProvider;
import appeng.tile.networking.TileCableBus;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileIOPort;
import appeng.tile.storage.TileMEChest;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

public final class TopNetworkDebugProvider {
    private TopNetworkDebugProvider() {
    }

    public static void addProbeInfo(EntityPlayer player, TileEntity blockEntity, Vec3d hitLocation,
                                    TopTooltipBuilder tooltipBuilder) {
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
            addLine(tooltipBuilder,
                line(TopNetDebugText.grid_pivot_pos.getLocal(), ": ")
                    .appendSibling(axis("X", snapshot.pivotPos().getX(), TextFormatting.RED))
                    .appendText(" ")
                    .appendSibling(axis("Y", snapshot.pivotPos().getY(), TextFormatting.GREEN))
                    .appendText(" ")
                    .appendSibling(axis("Z", snapshot.pivotPos().getZ(), TextFormatting.AQUA))
                    .appendSibling(context.isPivot() ? value(" [C]", TextFormatting.GRAY) : new TextComponentString("")));
        }
        addLine(tooltipBuilder, labeledValue(TopNetDebugText.grid_id.getLocal(), Integer.toString(snapshot.gridId())));
        addLine(tooltipBuilder, labeledValue(TopNetDebugText.grid_nodes.getLocal(), Integer.toString(snapshot.gridNodes())));
        addLine(tooltipBuilder, line(TopNetDebugText.grid_cpu_avg_max.getLocal(), ": ")
            .appendSibling(value(snapshot.cpuAverage(), TextFormatting.GREEN))
            .appendText(" / ")
            .appendSibling(value(snapshot.cpuMax(), TextFormatting.AQUA)));
        addLine(tooltipBuilder, labeledValue(TopNetDebugText.storage.getLocal(), snapshot.storage()));
        addLine(tooltipBuilder, labeledValue(TopNetDebugText.crafting.getLocal(), snapshot.crafting()));
        addLine(tooltipBuilder, labeledValue(TopNetDebugText.tick.getLocal(), snapshot.tick()));
        addLine(tooltipBuilder, labeledValue(TopNetDebugText.misc.getLocal(), snapshot.misc()));
    }

    private static void addLine(TopTooltipBuilder tooltipBuilder, ITextComponent line) {
        tooltipBuilder.addLine(line);
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
            || owner instanceof TileCraftingUnit
            || owner instanceof TileCraftingMonitor) {
            return Category.CRAFTING;
        }

        if (owner instanceof CablePart
            || owner instanceof P2PTunnelPart
            || owner instanceof TileCableBus) {
            return Category.TICK;
        }

        return Category.MISC;
    }

    private static ITextComponent labeledValue(String label, String value) {
        return line(label, ": ").appendSibling(value(value, TextFormatting.GREEN));
    }

    private static ITextComponent axis(String axis, int value, TextFormatting color) {
        return line(axis, ":").appendSibling(value(Integer.toString(value), color));
    }

    private static TextComponentString line(String label, String suffix) {
        TextComponentString component = new TextComponentString(label + suffix);
        component.setStyle(new Style().setColor(TextFormatting.WHITE));
        return component;
    }

    private static TextComponentString value(String value, TextFormatting color) {
        TextComponentString component = new TextComponentString(value);
        component.setStyle(new Style().setColor(color));
        return component;
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
