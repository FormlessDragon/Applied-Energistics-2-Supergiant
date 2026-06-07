package ae2.integration.modules.igtooltip;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.parts.IPartHost;
import ae2.api.parts.SelectedPart;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.me.Grid;
import ae2.me.InWorldGridNode;
import ae2.me.helpers.IGridConnectedTile;
import ae2.me.service.TickManagerService;
import ae2.tile.qnb.TileQuantumBridge;
import ae2.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

public final class NetworkDebugProvider {
    private static final String TAG_QUANTUM_BRIDGE = "ae2NetworkDebugQuantumBridge";
    private static final String TAG_QUANTUM_MISSING = "missing";
    private static final String TAG_QUANTUM_DIMENSION_NAME = "dimensionName";
    private static final String TAG_QUANTUM_DIMENSION_ID = "dimensionId";
    private static final String TAG_QUANTUM_X = "x";
    private static final String TAG_QUANTUM_Y = "y";
    private static final String TAG_QUANTUM_Z = "z";
    private static final String TAG_NETWORK_DEBUG = "ae2NetworkDebug";
    private static final String TAG_PART_NETWORK_DEBUG_PREFIX = "ae2NetworkDebugPart";
    private static final String TAG_GRID_PIVOT = "pivot";
    private static final String TAG_GRID_PIVOT_X = "x";
    private static final String TAG_GRID_PIVOT_Y = "y";
    private static final String TAG_GRID_PIVOT_Z = "z";
    private static final String TAG_GRID_IS_PIVOT = "isPivot";
    private static final String TAG_GRID_ID = "gridId";
    private static final String TAG_GRID_NODES = "gridNodes";
    private static final String TAG_TICK = "tick";
    private static final String TAG_TICK_CPU_AVERAGE = "cpuAverage";
    private static final String TAG_TICK_CPU_MAX = "cpuMax";
    private static final String TAG_TICK_STORAGE = "storage";
    private static final String TAG_TICK_CRAFTING = "crafting";
    private static final String TAG_TICK_TICK = "tick";
    private static final String TAG_TICK_MISC = "misc";

    private NetworkDebugProvider() {
    }

    public static void addProbeInfo(EntityPlayer player, TileEntity blockEntity, Vec3d hitLocation,
                                    TooltipBuilder tooltipBuilder) {
        if (blockEntity instanceof TileQuantumBridge quantumBridge) {
            addQuantumBridgeInfo(quantumBridge, tooltipBuilder);
        }

        if (!player.isSneaking()) {
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

        addNetworkIdentityInfo(concreteGrid, context, tooltipBuilder);

        if (!TickManagerService.MONITORING_ENABLED) {
            return;
        }

        addTickMonitoringInfo(concreteGrid, tooltipBuilder);
    }

    public static void provideServerData(EntityPlayer player, TileEntity blockEntity, NBTTagCompound serverData) {
        if (blockEntity instanceof TileQuantumBridge quantumBridge) {
            writeQuantumBridgeInfo(quantumBridge, serverData);
        }

        if (!player.isSneaking()) {
            return;
        }

        if (blockEntity instanceof IPartHost partHost) {
            for (var side : Platform.DIRECTIONS_WITH_NULL) {
                var part = partHost.getPart(side);
                if (part == null) {
                    continue;
                }

                var partData = buildNetworkData(part.getGridNode());
                if (partData != null) {
                    serverData.setTag(getPartNetworkDataName(side), partData);
                }
            }
            return;
        }

        var context = resolveContext(blockEntity, Vec3d.ZERO);
        if (context != null) {
            var networkData = buildNetworkData(context);
            if (networkData != null) {
                serverData.setTag(TAG_NETWORK_DEBUG, networkData);
            }
        }
    }

    public static void addProbeInfoFromServerData(TileEntity blockEntity, Vec3d hitLocation,
                                                  NBTTagCompound serverData, TooltipBuilder tooltipBuilder) {
        if (serverData.hasKey(TAG_QUANTUM_BRIDGE, Constants.NBT.TAG_COMPOUND)) {
            addQuantumBridgeInfo(serverData.getCompoundTag(TAG_QUANTUM_BRIDGE), tooltipBuilder);
        }

        var networkData = getNetworkData(blockEntity, hitLocation, serverData);
        if (networkData == null) {
            return;
        }

        addNetworkIdentityInfo(networkData, tooltipBuilder);
        if (networkData.hasKey(TAG_TICK, Constants.NBT.TAG_COMPOUND)) {
            addTickMonitoringInfo(networkData.getCompoundTag(TAG_TICK), tooltipBuilder);
        }
    }

    private static void addNetworkIdentityInfo(Grid grid, ResolvedContext context,
                                               TooltipBuilder tooltipBuilder) {
        BlockPos pivotPos = getPivotPos(grid);
        if (pivotPos != null) {
            tooltipBuilder.addLine(label(tooltipBuilder, TopText.netdebug_grid_pivot_pos)
                + axis("X", pivotPos.getX(), TextFormatting.RED)
                + " " + axis("Y", pivotPos.getY(), TextFormatting.GREEN)
                + " " + axis("Z", pivotPos.getZ(), TextFormatting.AQUA)
                + (context.isPivot() ? TextFormatting.GRAY + " [C]" : ""));
        }

        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_grid_id,
            Integer.toString(grid.getSerialNumber())));
        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_grid_nodes, Integer.toString(grid.size())));
    }

    private static void addNetworkIdentityInfo(NBTTagCompound networkData, TooltipBuilder tooltipBuilder) {
        if (networkData.hasKey(TAG_GRID_PIVOT, Constants.NBT.TAG_COMPOUND)) {
            var pivot = networkData.getCompoundTag(TAG_GRID_PIVOT);
            tooltipBuilder.addLine(label(tooltipBuilder, TopText.netdebug_grid_pivot_pos)
                + axis("X", pivot.getInteger(TAG_GRID_PIVOT_X), TextFormatting.RED)
                + " " + axis("Y", pivot.getInteger(TAG_GRID_PIVOT_Y), TextFormatting.GREEN)
                + " " + axis("Z", pivot.getInteger(TAG_GRID_PIVOT_Z), TextFormatting.AQUA)
                + (networkData.getBoolean(TAG_GRID_IS_PIVOT) ? TextFormatting.GRAY + " [C]" : ""));
        }

        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_grid_id,
            Integer.toString(networkData.getInteger(TAG_GRID_ID))));
        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_grid_nodes,
            Integer.toString(networkData.getInteger(TAG_GRID_NODES))));
    }

    private static void addTickMonitoringInfo(Grid grid, TooltipBuilder tooltipBuilder) {
        var snapshot = grid.getTickManager().getStatistics();
        tooltipBuilder.addLine(label(tooltipBuilder, TopText.netdebug_grid_cpu_avg_max)
            + value(Platform.formatTimeMeasurement(snapshot.cpuAverage()), TextFormatting.GREEN)
            + " / "
            + value(Platform.formatTimeMeasurement(snapshot.cpuMax()), TextFormatting.AQUA));
        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_storage,
            Platform.formatTimeMeasurement(snapshot.storage())));
        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_crafting,
            Platform.formatTimeMeasurement(snapshot.crafting())));
        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_tick,
            Platform.formatTimeMeasurement(snapshot.tick())));
        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_misc,
            Platform.formatTimeMeasurement(snapshot.misc())));
    }

    private static void addTickMonitoringInfo(NBTTagCompound tickData, TooltipBuilder tooltipBuilder) {
        tooltipBuilder.addLine(label(tooltipBuilder, TopText.netdebug_grid_cpu_avg_max)
            + value(Platform.formatTimeMeasurement(tickData.getLong(TAG_TICK_CPU_AVERAGE)), TextFormatting.GREEN)
            + " / "
            + value(Platform.formatTimeMeasurement(tickData.getLong(TAG_TICK_CPU_MAX)), TextFormatting.AQUA));
        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_storage,
            Platform.formatTimeMeasurement(tickData.getLong(TAG_TICK_STORAGE))));
        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_crafting,
            Platform.formatTimeMeasurement(tickData.getLong(TAG_TICK_CRAFTING))));
        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_tick,
            Platform.formatTimeMeasurement(tickData.getLong(TAG_TICK_TICK))));
        tooltipBuilder.addLine(labeledValue(tooltipBuilder, TopText.netdebug_misc,
            Platform.formatTimeMeasurement(tickData.getLong(TAG_TICK_MISC))));
    }

    private static void addQuantumBridgeInfo(TileQuantumBridge quantumBridge, TooltipBuilder tooltipBuilder) {
        var cluster = quantumBridge.getCluster();
        if (cluster == null) {
            tooltipBuilder.addLine(value(tooltipBuilder.localize(TopText.quantum_link_missing), TextFormatting.RED));
            return;
        }

        TileQuantumBridge linkedCenter = cluster.getLinkedCenter();
        if (linkedCenter == null || linkedCenter.getWorld() == null) {
            tooltipBuilder.addLine(value(tooltipBuilder.localize(TopText.quantum_link_missing), TextFormatting.RED));
            return;
        }

        String dimensionName = linkedCenter.getWorld().provider.getDimensionType().getName();
        int dimensionId = linkedCenter.getWorld().provider.getDimension();
        tooltipBuilder.addLine(label(tooltipBuilder, TopText.quantum_link_dimension)
            + value(dimensionName, TextFormatting.GREEN)
            + " " + value("(" + dimensionId + ")", TextFormatting.GREEN));

        BlockPos linkedPos = linkedCenter.getPos();
        tooltipBuilder.addLine(label(tooltipBuilder, TopText.quantum_link_position)
            + axis("X", linkedPos.getX(), TextFormatting.RED)
            + " " + axis("Y", linkedPos.getY(), TextFormatting.GREEN)
            + " " + axis("Z", linkedPos.getZ(), TextFormatting.AQUA));
    }

    private static void addQuantumBridgeInfo(NBTTagCompound quantumData, TooltipBuilder tooltipBuilder) {
        if (quantumData.getBoolean(TAG_QUANTUM_MISSING)) {
            tooltipBuilder.addLine(value(tooltipBuilder.localize(TopText.quantum_link_missing), TextFormatting.RED));
            return;
        }

        tooltipBuilder.addLine(label(tooltipBuilder, TopText.quantum_link_dimension)
            + value(quantumData.getString(TAG_QUANTUM_DIMENSION_NAME), TextFormatting.GREEN)
            + " " + value("(" + quantumData.getInteger(TAG_QUANTUM_DIMENSION_ID) + ")", TextFormatting.GREEN));
        tooltipBuilder.addLine(label(tooltipBuilder, TopText.quantum_link_position)
            + axis("X", quantumData.getInteger(TAG_QUANTUM_X), TextFormatting.RED)
            + " " + axis("Y", quantumData.getInteger(TAG_QUANTUM_Y), TextFormatting.GREEN)
            + " " + axis("Z", quantumData.getInteger(TAG_QUANTUM_Z), TextFormatting.AQUA));
    }

    private static void writeQuantumBridgeInfo(TileQuantumBridge quantumBridge, NBTTagCompound serverData) {
        var quantumData = new NBTTagCompound();
        var cluster = quantumBridge.getCluster();
        if (cluster == null) {
            quantumData.setBoolean(TAG_QUANTUM_MISSING, true);
            serverData.setTag(TAG_QUANTUM_BRIDGE, quantumData);
            return;
        }

        TileQuantumBridge linkedCenter = cluster.getLinkedCenter();
        if (linkedCenter == null || linkedCenter.getWorld() == null) {
            quantumData.setBoolean(TAG_QUANTUM_MISSING, true);
            serverData.setTag(TAG_QUANTUM_BRIDGE, quantumData);
            return;
        }

        quantumData.setString(TAG_QUANTUM_DIMENSION_NAME, linkedCenter.getWorld().provider.getDimensionType().getName());
        quantumData.setInteger(TAG_QUANTUM_DIMENSION_ID, linkedCenter.getWorld().provider.getDimension());
        BlockPos linkedPos = linkedCenter.getPos();
        quantumData.setInteger(TAG_QUANTUM_X, linkedPos.getX());
        quantumData.setInteger(TAG_QUANTUM_Y, linkedPos.getY());
        quantumData.setInteger(TAG_QUANTUM_Z, linkedPos.getZ());
        serverData.setTag(TAG_QUANTUM_BRIDGE, quantumData);
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

    private static @Nullable NBTTagCompound getNetworkData(TileEntity blockEntity, Vec3d hitLocation,
                                                           NBTTagCompound serverData) {
        if (blockEntity instanceof IPartHost partHost) {
            SelectedPart selected = partHost.selectPartWorld(hitLocation);
            if (selected.part == null) {
                return null;
            }

            var tagName = getPartNetworkDataName(selected.side);
            if (serverData.hasKey(tagName, Constants.NBT.TAG_COMPOUND)) {
                return serverData.getCompoundTag(tagName);
            }
            return null;
        }

        if (serverData.hasKey(TAG_NETWORK_DEBUG, Constants.NBT.TAG_COMPOUND)) {
            return serverData.getCompoundTag(TAG_NETWORK_DEBUG);
        }
        return null;
    }

    private static boolean isPivot(IGrid grid, IGridNode node) {
        return grid.getPivot() == node;
    }

    private static @Nullable BlockPos getPivotPos(Grid grid) {
        var pivot = grid.getPivot();
        if (pivot instanceof InWorldGridNode inWorldGridNode) {
            return inWorldGridNode.getLocation();
        }
        return null;
    }

    private static @Nullable NBTTagCompound buildNetworkData(@Nullable IGridNode node) {
        if (node == null) {
            return null;
        }
        return buildNetworkData(new ResolvedContext(node, isPivot(node.grid(), node)));
    }

    private static @Nullable NBTTagCompound buildNetworkData(ResolvedContext context) {
        var grid = context.node().grid();
        if (!(grid instanceof Grid concreteGrid)) {
            return null;
        }

        var networkData = new NBTTagCompound();
        BlockPos pivotPos = getPivotPos(concreteGrid);
        if (pivotPos != null) {
            var pivot = new NBTTagCompound();
            pivot.setInteger(TAG_GRID_PIVOT_X, pivotPos.getX());
            pivot.setInteger(TAG_GRID_PIVOT_Y, pivotPos.getY());
            pivot.setInteger(TAG_GRID_PIVOT_Z, pivotPos.getZ());
            networkData.setTag(TAG_GRID_PIVOT, pivot);
            networkData.setBoolean(TAG_GRID_IS_PIVOT, context.isPivot());
        }

        networkData.setInteger(TAG_GRID_ID, concreteGrid.getSerialNumber());
        networkData.setInteger(TAG_GRID_NODES, concreteGrid.size());

        if (TickManagerService.MONITORING_ENABLED) {
            var snapshot = concreteGrid.getTickManager().getStatistics();
            var tickData = new NBTTagCompound();
            tickData.setLong(TAG_TICK_CPU_AVERAGE, snapshot.cpuAverage());
            tickData.setLong(TAG_TICK_CPU_MAX, snapshot.cpuMax());
            tickData.setLong(TAG_TICK_STORAGE, snapshot.storage());
            tickData.setLong(TAG_TICK_CRAFTING, snapshot.crafting());
            tickData.setLong(TAG_TICK_TICK, snapshot.tick());
            tickData.setLong(TAG_TICK_MISC, snapshot.misc());
            networkData.setTag(TAG_TICK, tickData);
        }

        return networkData;
    }

    private static String getPartNetworkDataName(@Nullable EnumFacing location) {
        return TAG_PART_NETWORK_DEBUG_PREFIX + (location == null ? "center" : location.name());
    }

    private static String labeledValue(TooltipBuilder tooltip, TopText label, String value) {
        return label(tooltip, label) + value(value, TextFormatting.GREEN);
    }

    private static String axis(String axis, int value, TextFormatting color) {
        return axis + ":" + value(Integer.toString(value), color);
    }

    private static String label(TooltipBuilder tooltip, TopText label) {
        return tooltip.localize(label) + ": ";
    }

    private static String value(String value, TextFormatting color) {
        return color + value + TextFormatting.WHITE;
    }

    private record ResolvedContext(IGridNode node, boolean isPivot) {
    }
}
