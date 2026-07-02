package ae2.cellterminal.internal;

import ae2.api.cellterminal.CellTerminalScanner;
import ae2.api.cellterminal.CellTerminalSubnetConnection;
import ae2.api.cellterminal.CellTerminalSubnetTarget;
import ae2.api.cellterminal.CellTerminalTargetLocator;
import ae2.api.networking.IGrid;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.core.AppEng;
import ae2.helpers.InterfaceLogicHost;
import ae2.me.Grid;
import ae2.parts.misc.InterfacePart;
import ae2.parts.storagebus.StorageBusPart;
import ae2.tile.misc.TileInterface;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class InterfaceSubnetScanner implements CellTerminalScanner.Subnet {
    private static final ResourceLocation ID = AppEng.makeId("cell_terminal/interface_subnet_scanner");

    private static Iterable<EnumFacing> interfaceHostFacings(InterfaceLogicHost interfaceHost) {
        if (interfaceHost instanceof InterfacePart interfacePart && interfacePart.getSide() != null) {
            return List.of(interfacePart.getSide());
        }
        return List.of(EnumFacing.values());
    }

    private static @Nullable World interfaceHostLevel(InterfaceLogicHost interfaceHost) {
        if (interfaceHost instanceof TileInterface tileInterface) {
            return tileInterface.getWorld();
        }
        if (interfaceHost instanceof InterfacePart interfacePart) {
            return interfacePart.getLevel();
        }
        return null;
    }

    private static @Nullable BlockPos interfaceHostPos(InterfaceLogicHost interfaceHost) {
        if (interfaceHost instanceof TileInterface tileInterface) {
            return tileInterface.getPos();
        }
        if (interfaceHost instanceof InterfacePart interfacePart) {
            return interfacePart.getTileEntity().getPos();
        }
        return null;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public @NonNull List<? extends CellTerminalSubnetTarget> scan(IGrid grid) {
        var subnetBuilders = new Reference2ObjectOpenHashMap<IGrid, SubnetBuilder>();
        scanOutboundConnections(grid, subnetBuilders);
        scanInboundConnections(grid, subnetBuilders);

        var result = new ObjectArrayList<CellTerminalSubnetTarget>(subnetBuilders.size());
        for (var builder : subnetBuilders.values()) {
            String subnetId = builder.requirePersistentSubnetId();
            result.add(NativeCellTerminalTargets.createSubnetTarget(
                NativeCellTerminalTargets.stableSubnetId(builder.anchorLocator),
                subnetId,
                builder.anchorLocator,
                builder.displayName,
                builder.connections));
        }
        return List.copyOf(result);
    }

    private void scanOutboundConnections(IGrid mainGrid, Reference2ObjectMap<IGrid, SubnetBuilder> subnetBuilders) {
        for (var storageBus : StorageBusScanner.storageBuses(mainGrid)) {
            if (storageBus.getSide() == null) {
                continue;
            }

            InterfaceLogicHost interfaceHost = NativeCellTerminalTargets.findConnectedInterfaceHost(storageBus);
            if (interfaceHost == null) {
                continue;
            }

            Grid subnetGrid = NativeCellTerminalTargets.resolveInterfaceGrid(interfaceHost);
            if (subnetGrid == null || subnetGrid == mainGrid) {
                continue;
            }

            acceptBuilder(subnetBuilders, subnetGrid, interfaceHost)
                .addConnection(new CellTerminalSubnetConnection(
                    NativeCellTerminalTargets.createStorageBusTarget(storageBus), true));
        }
    }

    private void scanInboundConnections(IGrid mainGrid, Reference2ObjectMap<IGrid, SubnetBuilder> subnetBuilders) {
        var interfaceHosts = new ObjectArrayList<InterfaceLogicHost>();
        interfaceHosts.addAll(mainGrid.getMachines(TileInterface.class));
        interfaceHosts.addAll(mainGrid.getMachines(InterfacePart.class));
        for (InterfaceLogicHost interfaceHost : interfaceHosts) {
            World level = interfaceHostLevel(interfaceHost);
            BlockPos pos = interfaceHostPos(interfaceHost);
            if (level == null || pos == null) {
                continue;
            }
            for (EnumFacing facing : interfaceHostFacings(interfaceHost)) {
                TileEntity neighborTile = level.getTileEntity(pos.offset(facing));
                if (!(neighborTile instanceof IPartHost partHost)) {
                    continue;
                }
                IPart neighborPart = partHost.getPart(facing.getOpposite());
                if (!(neighborPart instanceof StorageBusPart storageBus) || storageBus.getSide() == null) {
                    continue;
                }
                if (!(storageBus.getMainNode().getGrid() instanceof Grid subnetGrid) || subnetGrid == mainGrid) {
                    continue;
                }
                acceptBuilder(subnetBuilders, subnetGrid, interfaceHost)
                    .addConnection(new CellTerminalSubnetConnection(
                        NativeCellTerminalTargets.createStorageBusTarget(storageBus), false));
            }
        }
    }

    private SubnetBuilder acceptBuilder(Reference2ObjectMap<IGrid, SubnetBuilder> subnetBuilders, IGrid subnetGrid,
                                        InterfaceLogicHost interfaceHost) {
        CellTerminalTargetLocator anchorLocator = NativeCellTerminalTargets.createSubnetAnchorLocator(interfaceHost);
        ITextComponent displayName = NativeCellTerminalTargets.describeInterfaceHost(interfaceHost);
        var builder = subnetBuilders.get(subnetGrid);
        if (builder == null) {
            builder = new SubnetBuilder(anchorLocator, displayName, interfaceHost);
            subnetBuilders.put(subnetGrid, builder);
        } else {
            builder.acceptAnchor(anchorLocator, displayName, interfaceHost);
        }
        return builder;
    }

    private static final class SubnetBuilder {
        private static final Comparator<CellTerminalTargetLocator> ANCHOR_ORDER =
            Comparator.comparing((CellTerminalTargetLocator locator) -> locator.kindId().toString())
                      .thenComparingInt(CellTerminalTargetLocator::dimensionId)
                      .thenComparingInt(locator -> locator.pos().getX())
                      .thenComparingInt(locator -> locator.pos().getY())
                      .thenComparingInt(locator -> locator.pos().getZ())
                      .thenComparing(locator -> locator.side() == null ? "" : locator.side().getName());
        private final List<CellTerminalSubnetConnection> connections = new ObjectArrayList<>();
        private final List<InterfaceLogicHost> anchors = new ObjectArrayList<>();
        private CellTerminalTargetLocator anchorLocator;
        private ITextComponent displayName;
        private String persistentSubnetId;

        private SubnetBuilder(CellTerminalTargetLocator anchorLocator, ITextComponent displayName,
                              InterfaceLogicHost interfaceHost) {
            this.anchorLocator = Objects.requireNonNull(anchorLocator, "anchorLocator");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            acceptPersistentSubnetId(interfaceHost);
        }

        private void acceptAnchor(CellTerminalTargetLocator anchorLocator, ITextComponent displayName,
                                  InterfaceLogicHost interfaceHost) {
            Objects.requireNonNull(anchorLocator, "anchorLocator");
            Objects.requireNonNull(displayName, "displayName");
            acceptPersistentSubnetId(interfaceHost);
            if (ANCHOR_ORDER.compare(anchorLocator, this.anchorLocator) < 0) {
                this.anchorLocator = anchorLocator;
                this.displayName = displayName;
            }
        }

        private void addConnection(CellTerminalSubnetConnection connection) {
            this.connections.add(Objects.requireNonNull(connection, "connection"));
        }

        private void acceptPersistentSubnetId(InterfaceLogicHost interfaceHost) {
            this.anchors.add(Objects.requireNonNull(interfaceHost, "interfaceHost"));
            String subnetId = interfaceHost.getCellTerminalSubnetId();
            if (subnetId != null && !subnetId.isEmpty()
                && (this.persistentSubnetId == null || subnetId.compareTo(this.persistentSubnetId) < 0)) {
                this.persistentSubnetId = subnetId;
            }
        }

        private String requirePersistentSubnetId() {
            String subnetId = this.persistentSubnetId;
            if (subnetId == null || subnetId.isEmpty()) {
                subnetId = "subnet@" + UUID.randomUUID();
            }
            for (var anchor : this.anchors) {
                if (!subnetId.equals(anchor.getCellTerminalSubnetId())) {
                    anchor.setCellTerminalSubnetId(subnetId);
                }
            }
            return subnetId;
        }
    }
}
