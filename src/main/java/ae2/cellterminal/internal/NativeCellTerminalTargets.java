package ae2.cellterminal.internal;

import ae2.api.cellterminal.CellTerminalBusPartitionMode;
import ae2.api.cellterminal.CellTerminalBusTarget;
import ae2.api.cellterminal.CellTerminalBusTextPartitionSnapshot;
import ae2.api.cellterminal.CellTerminalCapability;
import ae2.api.cellterminal.CellTerminalCellSlotMutation;
import ae2.api.cellterminal.CellTerminalCellSlotTarget;
import ae2.api.cellterminal.CellTerminalContentSnapshot;
import ae2.api.cellterminal.CellTerminalIoFilterMode;
import ae2.api.cellterminal.CellTerminalPartitionSnapshot;
import ae2.api.cellterminal.CellTerminalStorageTarget;
import ae2.api.cellterminal.CellTerminalSubnetConnection;
import ae2.api.cellterminal.CellTerminalSubnetTarget;
import ae2.api.cellterminal.CellTerminalTarget;
import ae2.api.cellterminal.CellTerminalTargetLocator;
import ae2.api.cellterminal.CellTerminalUpgradeSnapshot;
import ae2.api.config.AccessRestriction;
import ae2.api.config.FuzzyMode;
import ae2.api.config.Settings;
import ae2.api.config.StorageFilter;
import ae2.api.config.YesNo;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.parts.IPartHost;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.cells.CellState;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.storage.cells.StorageCell;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.core.AELog;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.helpers.InterfaceLogicHost;
import ae2.me.Grid;
import ae2.me.cells.BasicCellInventory;
import ae2.parts.AEBasePart;
import ae2.parts.automation.special.ModFilterHost;
import ae2.parts.automation.special.ODFilterHost;
import ae2.parts.automation.special.PreciseStorageBusPart;
import ae2.parts.misc.InterfacePart;
import ae2.parts.storagebus.StorageBusPart;
import ae2.tile.misc.TileInterface;
import ae2.tile.storage.TileDrive;
import ae2.tile.storage.TileMEChest;
import ae2.util.ConfigInventory;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class NativeCellTerminalTargets {
    private static final Set<Block> STORAGE_BUS_PICK_BLOCK_FAILURES = new ObjectOpenHashSet<>();

    private NativeCellTerminalTargets() {
    }

    static CellTerminalStorageTarget createDriveStorageTarget(TileDrive drive) {
        return new DriveStorageTarget(drive);
    }

    static CellTerminalStorageTarget createMEChestStorageTarget(TileMEChest chest) {
        return new MEChestStorageTarget(chest);
    }

    static CellTerminalBusTarget createStorageBusTarget(StorageBusPart storageBus) {
        return new StorageBusTargetImpl(storageBus);
    }

    static CellTerminalSubnetTarget createSubnetTarget(String stableTargetId, String subnetId,
                                                       CellTerminalTargetLocator locator,
                                                       ITextComponent displayName,
                                                       List<CellTerminalSubnetConnection> connections) {
        return new SubnetTargetImpl(stableTargetId, subnetId, locator, displayName, connections);
    }

    public static CellTerminalBusTarget resolveStorageBusTarget(CellTerminalTargetLocator locator) {
        return new StorageBusTargetImpl(CellTerminalTargetResolver.requireStorageBus(locator));
    }

    public static CellTerminalSubnetTarget resolveSubnetTarget(String stableTargetId, CellTerminalTargetLocator locator) {
        InterfaceLogicHost interfaceHost = requireResolvedInterfaceTarget(stableTargetId, locator);
        return new SubnetTargetImpl(
            stableSubnetStableTargetId(interfaceHost),
            persistentSubnetId(interfaceHost),
            createSubnetAnchorLocator(interfaceHost),
            describeInterfaceHost(interfaceHost),
            List.of());
    }

    public static CellTerminalStorageTarget resolveStorageTarget(CellTerminalTargetLocator locator) {
        if (CellTerminalTargetResolver.DRIVE_KIND.equals(locator.kindId())) {
            return new DriveStorageTarget(CellTerminalTargetResolver.requireDrive(locator));
        }
        if (CellTerminalTargetResolver.ME_CHEST_KIND.equals(locator.kindId())) {
            return new MEChestStorageTarget(CellTerminalTargetResolver.requireMEChest(locator));
        }
        if (CellTerminalTargetResolver.STORAGE_BUS_KIND.equals(locator.kindId())) {
            return resolveStorageBusTarget(locator);
        }
        CellTerminalWriteSupport.fail("Unsupported Cell Terminal storage target locator: %s", locator);
        throw new IllegalStateException("unreachable");
    }

    public static CellTerminalStorageTarget requireResolvedStorageTarget(String stableTargetId,
                                                                         CellTerminalTargetLocator locator) {
        CellTerminalStorageTarget liveTarget = resolveStorageTarget(locator);
        requireStableTarget(stableTargetId, liveTarget, locator);
        return liveTarget;
    }

    public static StorageBusPart requireResolvedStorageBus(String stableTargetId, CellTerminalTargetLocator locator) {
        CellTerminalBusTarget liveTarget = resolveStorageBusTarget(locator);
        requireStableTarget(stableTargetId, liveTarget, locator);
        return ((StorageBusTargetImpl) liveTarget).storageBus;
    }

    public static InterfaceLogicHost requireResolvedInterfaceTarget(String stableTargetId, CellTerminalTargetLocator locator) {
        InterfaceLogicHost liveTarget = CellTerminalTargetResolver.requireInterfaceHost(locator);
        CellTerminalTargetLocator liveLocator = createSubnetAnchorLocator(liveTarget);
        String liveStableTargetId = stableSubnetStableTargetId(liveTarget);
        if (!Objects.requireNonNull(stableTargetId, "stableTargetId").equals(liveStableTargetId)) {
            CellTerminalWriteSupport.fail(
                "Cell Terminal subnet stableTargetId mismatch. requested=%s, resolved=%s, locator=%s",
                stableTargetId,
                liveStableTargetId,
                locator);
        }
        if (!liveLocator.equals(locator)) {
            CellTerminalWriteSupport.fail(
                "Cell Terminal subnet locator changed during resolve. requested=%s, resolved=%s",
                locator,
                liveLocator);
        }
        return liveTarget;
    }

    static @Nullable InterfaceLogicHost findConnectedInterfaceHost(StorageBusPart storageBus) {
        EnumFacing side = storageBus.getSide();
        if (side == null) {
            return null;
        }

        BlockPos targetPos = storageBus.getTileEntity().getPos().offset(side);
        var targetTile = storageBus.getLevel().getTileEntity(targetPos);
        if (targetTile instanceof TileInterface tileInterface) {
            return tileInterface;
        }
        if (targetTile instanceof IPartHost partHost) {
            var targetPart = partHost.getPart(side.getOpposite());
            if (targetPart instanceof InterfacePart interfacePart) {
                return interfacePart;
            }
        }
        return null;
    }

    private static ItemStack resolveStorageBusAttachedIcon(StorageBusPart storageBus) {
        EnumFacing side = storageBus.getSide();
        if (side == null) {
            return storageBus.getPartItem().asItemStack();
        }
        World level = storageBus.getLevel();
        BlockPos targetPos = storageBus.getTileEntity().getPos().offset(side);
        ItemStack targetStack = getTargetDisplayStack(level, targetPos, side);
        return targetStack.isEmpty() ? storageBus.getPartItem().asItemStack() : targetStack;
    }

    private static ItemStack getTargetDisplayStack(World level, BlockPos pos, EnumFacing storageBusSide) {
        IBlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        try {
            RayTraceResult hit = getTargetHit(level, pos, storageBusSide);
            if (hit != null && pos.equals(hit.getBlockPos())) {
                ItemStack picked = block.getPickBlock(state, hit, level, pos, null);
                if (!picked.isEmpty()) {
                    return picked;
                }
            }
        } catch (Throwable e) {
            if (e instanceof VirtualMachineError fatal) {
                throw fatal;
            }
            if (STORAGE_BUS_PICK_BLOCK_FAILURES.add(block)) {
                AELog.warn(e, "Failed to get pick block stack for Cell Terminal storage bus target %s at %s",
                    block, pos);
            }
        }
        Item item = Item.getItemFromBlock(block);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        int metadata = item.getHasSubtypes() ? block.getMetaFromState(state) : 0;
        return new ItemStack(item, 1, metadata);
    }

    @Nullable
    private static RayTraceResult getTargetHit(World level, BlockPos pos, EnumFacing storageBusSide) {
        EnumFacing targetToBus = storageBusSide.getOpposite();
        Vec3d from = new Vec3d(
            pos.getX() + 0.5D - targetToBus.getXOffset() * 0.499D,
            pos.getY() + 0.5D - targetToBus.getYOffset() * 0.499D,
            pos.getZ() + 0.5D - targetToBus.getZOffset() * 0.499D);
        Vec3d to = from.add(
            targetToBus.getXOffset(),
            targetToBus.getYOffset(),
            targetToBus.getZOffset());
        return level.rayTraceBlocks(from, to, true);
    }

    public static @Nullable Grid resolveInterfaceGrid(InterfaceLogicHost interfaceHost) {
        if (interfaceHost instanceof TileInterface tileInterface) {
            return tileInterface.getMainNode().getGrid() instanceof Grid grid ? grid : null;
        }
        if (interfaceHost instanceof InterfacePart interfacePart) {
            return interfacePart.getMainNode().getGrid() instanceof Grid grid ? grid : null;
        }
        return null;
    }

    static CellTerminalTargetLocator createSubnetAnchorLocator(InterfaceLogicHost interfaceHost) {
        Objects.requireNonNull(interfaceHost, "interfaceHost");
        if (interfaceHost instanceof TileInterface tileInterface) {
            return buildLocator(
                CellTerminalTargetResolver.INTERFACE_TILE_KIND,
                tileInterface.getWorld().provider.getDimension(),
                tileInterface.getPos(),
                null);
        }
        if (interfaceHost instanceof InterfacePart interfacePart) {
            return buildLocator(
                CellTerminalTargetResolver.INTERFACE_PART_KIND,
                interfacePart.getLevel().provider.getDimension(),
                interfacePart.getTileEntity().getPos(),
                Objects.requireNonNull(interfacePart.getSide(), "Interface part side is required for subnet locator."));
        }

        throw new IllegalArgumentException("Unsupported subnet interface host: " + interfaceHost.getClass().getName());
    }

    static String stableSubnetId(CellTerminalTargetLocator locator) {
        Objects.requireNonNull(locator, "locator");
        return String.format(
            Locale.ROOT,
            "subnet@%s:%d:%d:%d:%d:%s",
            locator.kindId(),
            locator.dimensionId(),
            locator.pos().getX(),
            locator.pos().getY(),
            locator.pos().getZ(),
            locator.side() == null ? "tile" : locator.side().getName());
    }

    static ITextComponent describeInterfaceHost(InterfaceLogicHost interfaceHost) {
        Objects.requireNonNull(interfaceHost, "interfaceHost");
        if (interfaceHost instanceof AEBasePart part) {
            return new TextComponentString(part.getDisplayName());
        }
        if (interfaceHost instanceof TileInterface tileInterface) {
            return textOf(tileInterface.getItemFromTile());
        }
        return GuiText.Interface.text();
    }

    static String stableSubnetStableTargetId(InterfaceLogicHost interfaceHost) {
        return stableSubnetId(createSubnetAnchorLocator(interfaceHost));
    }

    static String persistentSubnetId(InterfaceLogicHost interfaceHost) {
        String subnetId = interfaceHost.getCellTerminalSubnetId();
        if (subnetId == null || subnetId.isEmpty()) {
            CellTerminalWriteSupport.fail("Cell Terminal subnet interface is missing persistent subnet id: %s",
                createSubnetAnchorLocator(interfaceHost));
        }
        return subnetId;
    }

    private static void requireStableTarget(String stableTargetId, CellTerminalTarget liveTarget,
                                            CellTerminalTargetLocator locator) {
        if (!Objects.requireNonNull(stableTargetId, "stableTargetId").equals(liveTarget.stableTargetId())) {
            CellTerminalWriteSupport.fail(
                "Cell Terminal target stableTargetId mismatch. requested=%s, resolved=%s, locator=%s",
                stableTargetId,
                liveTarget.stableTargetId(),
                locator);
        }
        if (!liveTarget.locator().equals(locator)) {
            CellTerminalWriteSupport.fail(
                "Cell Terminal target locator changed during resolve. requested=%s, resolved=%s",
                locator,
                liveTarget.locator());
        }
    }

    private static ITextComponent textOf(ItemStack stack) {
        return new TextComponentString(stack.getDisplayName());
    }

    private static List<CellTerminalCellSlotTarget> createDriveCellSlots(CellTerminalStorageTarget storageTarget,
                                                                         TileDrive drive) {
        var cellSlots = new ObjectArrayList<CellTerminalCellSlotTarget>(drive.getCellCount());
        for (int slotIndex = 0; slotIndex < drive.getCellCount(); slotIndex++) {
            cellSlots.add(new DriveCellSlotTarget(storageTarget, drive, slotIndex));
        }
        return List.copyOf(cellSlots);
    }

    private static String buildStableId(String type, int dimensionId, BlockPos pos, EnumFacing side) {
        var builder = new StringBuilder(64)
            .append(type)
            .append("@")
            .append(dimensionId)
            .append(":")
            .append(pos.getX())
            .append(",")
            .append(pos.getY())
            .append(",")
            .append(pos.getZ());
        if (side != null) {
            builder.append("#").append(side.getIndex());
        }
        return builder.toString();
    }

    private static CellTerminalTargetLocator buildLocator(ResourceLocation kindId, int dimensionId, BlockPos pos,
                                                          @Nullable EnumFacing side) {
        return new CellTerminalTargetLocator(kindId, dimensionId, pos.toImmutable(), side);
    }

    private static final class DriveStorageTarget extends AbstractTarget {
        private final TileDrive drive;
        private final List<CellTerminalCellSlotTarget> cellSlots;

        private DriveStorageTarget(TileDrive drive) {
            super(
                buildStableId("drive", drive.getWorld().provider.getDimension(), drive.getPos(), null),
                textOf(drive.getItemFromTile()),
                buildLocator(CellTerminalTargetResolver.DRIVE_KIND, drive.getWorld().provider.getDimension(),
                    drive.getPos(), null),
                drive.getItemFromTile());
            this.drive = Objects.requireNonNull(drive, "drive");
            this.cellSlots = createDriveCellSlots(this, drive);
        }

        @Override
        public int getPriority() {
            return this.drive.getPriority();
        }

        @Override
        public int getCellSlotCount() {
            return this.drive.getCellCount();
        }

        @Override
        public int getMountedCellCount() {
            int mountedCellCount = 0;
            for (int slot = 0; slot < this.drive.getCellCount(); slot++) {
                if (!this.drive.getInternalInventory().getStackInSlot(slot).isEmpty()) {
                    mountedCellCount++;
                }
            }
            return mountedCellCount;
        }

        @Override
        public List<? extends CellTerminalCellSlotTarget> getCellSlots() {
            return this.cellSlots;
        }
    }

    private static final class MEChestStorageTarget extends AbstractTarget {
        private final TileMEChest chest;
        private final List<CellTerminalCellSlotTarget> cellSlots;

        private MEChestStorageTarget(TileMEChest chest) {
            super(
                buildStableId("me_chest", chest.getWorld().provider.getDimension(), chest.getPos(), null),
                textOf(chest.getItemFromTile()),
                buildLocator(CellTerminalTargetResolver.ME_CHEST_KIND, chest.getWorld().provider.getDimension(),
                    chest.getPos(), null),
                chest.getItemFromTile());
            this.chest = Objects.requireNonNull(chest, "chest");
            this.cellSlots = List.of(new MEChestCellSlotTarget(this, chest, 0));
        }

        @Override
        public int getPriority() {
            return this.chest.getPriority();
        }

        @Override
        public int getCellSlotCount() {
            return this.chest.getCellCount();
        }

        @Override
        public int getMountedCellCount() {
            return this.chest.getCell().isEmpty() ? 0 : 1;
        }

        @Override
        public List<? extends CellTerminalCellSlotTarget> getCellSlots() {
            return this.cellSlots;
        }
    }

    private abstract static class AbstractTarget implements CellTerminalStorageTarget {
        private final String stableTargetId;
        private final ITextComponent displayName;
        private final CellTerminalTargetLocator locator;
        private final ItemStack icon;

        protected AbstractTarget(String stableTargetId, ITextComponent displayName, CellTerminalTargetLocator locator,
                                 ItemStack icon) {
            this.stableTargetId = Objects.requireNonNull(stableTargetId, "stableTargetId");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.locator = Objects.requireNonNull(locator, "locator");
            this.icon = icon == null ? ItemStack.EMPTY : icon;
        }

        @Override
        public final String stableTargetId() {
            return this.stableTargetId;
        }

        @Override
        public final ITextComponent displayName() {
            return this.displayName;
        }

        @Override
        public final ItemStack icon() {
            return this.icon;
        }

        @Override
        public final CellTerminalTargetLocator locator() {
            return this.locator;
        }

        @Override
        public boolean supportsCapability(CellTerminalCapability capability) {
            return switch (capability) {
                case CONTENT_PREVIEW, CELL_SLOT_WRITE, PARTITION_WRITE, UPGRADE_WRITE, PRIORITY_WRITE -> true;
                case AUTO_PARTITION_FROM_CONTENT, TEXT_PARTITION_WRITE, PRECISE_PARTITION_WRITE,
                     SAFE_UNIQUE_TYPE_REALLOCATION,
                     IO_FILTER_MODE_WRITE, SUBNET_RESOLVE -> false;
            };
        }

        @Override
        public final CellTerminalContentSnapshot previewContent() {
            KeyCounter counter = new KeyCounter();
            for (var slot : getCellSlots()) {
                StorageCell cell = slot.getCellInventory();
                if (cell != null) {
                    cell.getAvailableStacks(counter);
                }
            }
            return CellTerminalContentSnapshot.fromCounter(counter);
        }

        @Override
        public final void setPriority(int priority) {
            CellTerminalStorageTarget liveTarget = requireResolvedStorageTarget(this.stableTargetId, this.locator);
            switch (liveTarget) {
                case DriveStorageTarget driveTarget -> {
                    driveTarget.drive.setPriority(priority);
                    return;
                }
                case MEChestStorageTarget chestTarget -> {
                    chestTarget.chest.setPriority(priority);
                    return;
                }
                case StorageBusTargetImpl busTarget -> {
                    busTarget.storageBus.setPriority(priority);
                    return;
                }
                default -> {
                }
            }
            CellTerminalWriteSupport.fail("Unsupported priority write target: %s", liveTarget.getClass().getName());
        }
    }

    private static final class StorageBusTargetImpl implements CellTerminalBusTarget {
        private final StorageBusPart storageBus;
        private final String stableTargetId;
        private final ITextComponent displayName;
        private final @Nullable ITextComponent connectedDisplayName;
        private final CellTerminalTargetLocator locator;
        private final ItemStack icon;

        private StorageBusTargetImpl(StorageBusPart storageBus) {
            this.storageBus = Objects.requireNonNull(storageBus, "storageBus");
            this.stableTargetId = buildStableId("storage_bus",
                storageBus.getLevel().provider.getDimension(),
                storageBus.getTileEntity().getPos(),
                Objects.requireNonNull(storageBus.getSide(), "Storage bus side is required for stable id."));
            this.displayName = new TextComponentString(storageBus.getDisplayName());
            this.connectedDisplayName = storageBus.getConnectedToDescription();
            this.locator = buildLocator(CellTerminalTargetResolver.STORAGE_BUS_KIND,
                storageBus.getLevel().provider.getDimension(),
                storageBus.getTileEntity().getPos(),
                storageBus.getSide());
            this.icon = resolveStorageBusAttachedIcon(storageBus);
        }

        private static int getPartitionSlotCapacity(StorageBusPart storageBus) {
            if (!isSlotPartitionMode(getPartitionMode(storageBus))) {
                return 0;
            }
            int capacityCards = storageBus.getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD.item());
            return Math.min(18 + capacityCards * 9, storageBus.getConfig().size());
        }

        private static CellTerminalBusPartitionMode getPartitionMode(StorageBusPart storageBus) {
            if (storageBus instanceof ModFilterHost) {
                return CellTerminalBusPartitionMode.MOD_EXPRESSION;
            }
            if (storageBus instanceof ODFilterHost) {
                return CellTerminalBusPartitionMode.ORE_DICTIONARY_EXPRESSIONS;
            }
            if (storageBus instanceof PreciseStorageBusPart) {
                return CellTerminalBusPartitionMode.PRECISE_SLOTS;
            }
            return CellTerminalBusPartitionMode.SLOTS;
        }

        private static boolean isSlotPartitionMode(CellTerminalBusPartitionMode mode) {
            return mode == CellTerminalBusPartitionMode.SLOTS
                || mode == CellTerminalBusPartitionMode.PRECISE_SLOTS;
        }

        private static boolean isTextPartitionMode(CellTerminalBusPartitionMode mode) {
            return mode == CellTerminalBusPartitionMode.MOD_EXPRESSION
                || mode == CellTerminalBusPartitionMode.ORE_DICTIONARY_EXPRESSIONS;
        }

        private static void setPartition(ConfigInventory inventory,
                                         List<? extends @Nullable GenericStack> partitionSlots,
                                         int capacity) {
            Objects.requireNonNull(inventory, "inventory");
            Objects.requireNonNull(partitionSlots, "partitionSlots");
            if (partitionSlots.size() > capacity) {
                CellTerminalWriteSupport.fail(
                    "Cell Terminal storage bus partition write exceeds active capacity. size=%d, capacity=%d",
                    partitionSlots.size(), capacity);
            }

            for (int slot = 0; slot < capacity; slot++) {
                GenericStack stack = slot < partitionSlots.size() ? partitionSlots.get(slot) : null;
                if (stack != null && !inventory.isAllowedIn(slot, stack.what())) {
                    CellTerminalWriteSupport.fail("Cell Terminal storage bus partition slot rejected key. slot=%d, key=%s",
                        slot, stack.what());
                }
            }

            inventory.beginBatch();
            try {
                for (int slot = 0; slot < capacity; slot++) {
                    GenericStack stack = slot < partitionSlots.size() ? partitionSlots.get(slot) : null;
                    inventory.setStack(slot, stack);
                }
            } finally {
                inventory.endBatch();
            }
        }

        @Override
        public String stableTargetId() {
            return this.stableTargetId;
        }

        @Override
        public ItemStack icon() {
            return this.icon;
        }

        @Override
        public ITextComponent displayName() {
            return this.displayName;
        }

        @Override
        public @Nullable ITextComponent connectedDisplayName() {
            return this.connectedDisplayName;
        }

        @Override
        public CellTerminalTargetLocator locator() {
            return this.locator;
        }

        @Override
        public int getPriority() {
            return this.storageBus.getPriority();
        }

        @Override
        public void setPriority(int priority) {
            requireLiveStorageBus().setPriority(priority);
        }

        @Override
        public int getCellSlotCount() {
            return 0;
        }

        @Override
        public int getMountedCellCount() {
            return 0;
        }

        @Override
        public List<? extends CellTerminalCellSlotTarget> getCellSlots() {
            return List.of();
        }

        @Override
        public AccessRestriction getAccessRestriction() {
            return this.storageBus.getConfigManager().getSetting(Settings.ACCESS);
        }

        @Override
        public StorageFilter getStorageFilter() {
            return this.storageBus.getConfigManager().getSetting(Settings.STORAGE_FILTER);
        }

        @Override
        public YesNo getFilterOnExtract() {
            return this.storageBus.getConfigManager().getSetting(Settings.FILTER_ON_EXTRACT);
        }

        @Override
        public boolean isExtractableOnly() {
            return getStorageFilter() == StorageFilter.EXTRACTABLE_ONLY;
        }

        @Override
        public KeyCounter getAvailableStacks() {
            return this.storageBus.getInternalHandler().getAvailableStacks();
        }

        @Override
        public ConfigInventory getConfigInventory() {
            return this.storageBus.getConfig();
        }

        @Override
        public int getPartitionSlotCapacity() {
            return getPartitionSlotCapacity(this.storageBus);
        }

        @Override
        public IUpgradeInventory getUpgradeInventory() {
            return this.storageBus.getUpgrades();
        }

        @Override
        public boolean supportsCapability(CellTerminalCapability capability) {
            return switch (capability) {
                case CONTENT_PREVIEW, UPGRADE_WRITE, PRIORITY_WRITE, IO_FILTER_MODE_WRITE -> true;
                case PARTITION_WRITE -> isSlotPartitionMode(getPartitionMode());
                case PRECISE_PARTITION_WRITE -> getPartitionMode() == CellTerminalBusPartitionMode.PRECISE_SLOTS;
                case TEXT_PARTITION_WRITE -> isTextPartitionMode(getPartitionMode());
                case AUTO_PARTITION_FROM_CONTENT, CELL_SLOT_WRITE, SAFE_UNIQUE_TYPE_REALLOCATION, SUBNET_RESOLVE ->
                    false;
            };
        }

        @Override
        public CellTerminalContentSnapshot previewContent() {
            return CellTerminalContentSnapshot.fromCounter(getAvailableStacks());
        }

        @Override
        public FuzzyMode getFuzzyMode() {
            return this.storageBus.getConfigManager().getSetting(Settings.FUZZY_MODE);
        }

        @Override
        public CellTerminalIoFilterMode getIoFilterMode() {
            return new CellTerminalIoFilterMode(
                getAccessRestriction(),
                getStorageFilter(),
                getFilterOnExtract(),
                getFuzzyMode());
        }

        @Override
        public void setIoFilterMode(CellTerminalIoFilterMode mode) {
            Objects.requireNonNull(mode, "mode");
            StorageBusPart live = requireLiveStorageBus();
            live.getConfigManager().putSetting(Settings.ACCESS, mode.accessRestriction());
            live.getConfigManager().putSetting(Settings.STORAGE_FILTER, mode.storageFilter());
            live.getConfigManager().putSetting(Settings.FILTER_ON_EXTRACT, mode.filterOnExtract());
            live.getConfigManager().putSetting(Settings.FUZZY_MODE, mode.fuzzyMode());
        }

        @Override
        public CellTerminalPartitionSnapshot getPartitionSnapshot() {
            if (!isSlotPartitionMode(getPartitionMode())) {
                return new CellTerminalPartitionSnapshot(List.of());
            }
            ConfigInventory inventory = this.storageBus.getConfig();
            int capacity = getPartitionSlotCapacity();
            var entries = new ObjectArrayList<@Nullable GenericStack>(capacity);
            for (int slot = 0; slot < capacity; slot++) {
                entries.add(inventory.getStack(slot));
            }
            return new CellTerminalPartitionSnapshot(entries);
        }

        @Override
        public CellTerminalBusPartitionMode getPartitionMode() {
            if (this.storageBus instanceof ModFilterHost) {
                return CellTerminalBusPartitionMode.MOD_EXPRESSION;
            }
            if (this.storageBus instanceof ODFilterHost) {
                return CellTerminalBusPartitionMode.ORE_DICTIONARY_EXPRESSIONS;
            }
            if (this.storageBus instanceof PreciseStorageBusPart) {
                return CellTerminalBusPartitionMode.PRECISE_SLOTS;
            }
            return CellTerminalBusPartitionMode.SLOTS;
        }

        @Override
        public CellTerminalBusTextPartitionSnapshot getTextPartitionSnapshot() {
            if (this.storageBus instanceof ModFilterHost modFilterHost) {
                return new CellTerminalBusTextPartitionSnapshot(modFilterHost.getModFilter(), "");
            }
            if (this.storageBus instanceof ODFilterHost odFilterHost) {
                return new CellTerminalBusTextPartitionSnapshot(
                    odFilterHost.getODFilter(true),
                    odFilterHost.getODFilter(false));
            }
            return CellTerminalBusTextPartitionSnapshot.empty();
        }

        @Override
        public void setPartition(List<? extends @Nullable GenericStack> partitionSlots) {
            StorageBusPart live = requireLiveStorageBus();
            if (!isSlotPartitionMode(getPartitionMode(live))) {
                CellTerminalWriteSupport.fail("Cell Terminal storage bus does not expose slot partition. target=%s",
                    this.stableTargetId);
            }
            setPartition(live.getConfig(), partitionSlots, getPartitionSlotCapacity(live));
        }

        @Override
        public void setTextPartition(String fieldId, String expression) {
            Objects.requireNonNull(fieldId, "fieldId");
            StorageBusPart live = requireLiveStorageBus();
            expression = expression == null ? "" : expression;
            if (live instanceof ModFilterHost modFilterHost && "mod".equals(fieldId)) {
                modFilterHost.setModFilter(expression);
                return;
            }
            if (live instanceof ODFilterHost odFilterHost) {
                if ("odWhite".equals(fieldId)) {
                    odFilterHost.setODFilter(expression, true);
                    return;
                }
                if ("odBlack".equals(fieldId)) {
                    odFilterHost.setODFilter(expression, false);
                    return;
                }
            }
            CellTerminalWriteSupport.fail(
                "Cell Terminal storage bus text partition write rejected. target=%s, field=%s, mode=%s",
                this.stableTargetId, fieldId, getPartitionMode(live));
        }

        @Override
        public CellTerminalUpgradeSnapshot getUpgradeSnapshot() {
            return CellTerminalWriteSupport.snapshotUpgrades(this.storageBus.getUpgrades());
        }

        @Override
        public void setUpgrades(List<ItemStack> upgradeStacks) {
            StorageBusPart live = requireLiveStorageBus();
            CellTerminalWriteSupport.setUpgrades(live.getUpgrades(), upgradeStacks);
            live.upgradesChanged();
        }

        private StorageBusPart requireLiveStorageBus() {
            return requireResolvedStorageBus(this.stableTargetId, this.locator);
        }
    }

    private record SubnetTargetImpl(String stableTargetId, String subnetId, CellTerminalTargetLocator locator,
                                    ITextComponent displayName,
                                    List<CellTerminalSubnetConnection> connections) implements CellTerminalSubnetTarget {
        private SubnetTargetImpl(String stableTargetId, String subnetId, CellTerminalTargetLocator locator,
                                 ITextComponent displayName, List<CellTerminalSubnetConnection> connections) {
            this.stableTargetId = Objects.requireNonNull(stableTargetId, "stableTargetId");
            this.subnetId = Objects.requireNonNull(subnetId, "subnetId");
            this.locator = Objects.requireNonNull(locator, "locator");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.connections = List.copyOf(Objects.requireNonNull(connections, "connections"));
        }

        @Override
        public List<CellTerminalSubnetConnection> getConnections() {
            return this.connections;
        }

        @Override
        public boolean supportsCapability(CellTerminalCapability capability) {
            return capability == CellTerminalCapability.SUBNET_RESOLVE;
        }

        @Override
        public IGrid resolveSubnet() {
            InterfaceLogicHost interfaceHost = requireResolvedInterfaceTarget(this.stableTargetId, this.locator);
            Grid grid = resolveInterfaceGrid(interfaceHost);
            if (grid == null) {
                CellTerminalWriteSupport.fail("Cell Terminal subnet resolve lost grid. target=%s", this.stableTargetId);
            }
            return grid;
        }
    }

    private abstract static class AbstractCellSlotTarget implements CellTerminalCellSlotTarget {
        private final CellTerminalStorageTarget storageTarget;
        private final int slotIndex;

        protected AbstractCellSlotTarget(CellTerminalStorageTarget storageTarget, int slotIndex) {
            if (slotIndex < 0) {
                throw new IllegalArgumentException("slotIndex must be >= 0");
            }
            this.storageTarget = Objects.requireNonNull(storageTarget, "storageTarget");
            this.slotIndex = slotIndex;
        }

        private static AbstractCellSlotTarget requireNativeCellSlot(CellTerminalCellSlotTarget liveSlot) {
            if (liveSlot instanceof AbstractCellSlotTarget nativeSlot) {
                return nativeSlot;
            }
            CellTerminalWriteSupport.fail("Unsupported live cell slot implementation: %s",
                liveSlot.getClass().getName());
            throw new IllegalStateException("unreachable");
        }

        @Override
        public final CellTerminalStorageTarget getStorageTarget() {
            return this.storageTarget;
        }

        @Override
        public final int slotIndex() {
            return this.slotIndex;
        }

        @Override
        public final boolean isMounted() {
            return getMountedCellInventory() != null;
        }

        @Override
        public final @Nullable StorageCell getCellInventory() {
            return getMountedCellInventory();
        }

        @Override
        public final boolean supportsCapability(CellTerminalCapability capability) {
            if (capability == CellTerminalCapability.AUTO_PARTITION_FROM_CONTENT) {
                ItemStack stack = getCellStack();
                return !stack.isEmpty()
                    && stack.getItem() instanceof ICellWorkbenchItem workbenchItem
                    && workbenchItem.supportsAutoPartition(stack);
            }
            if (capability == CellTerminalCapability.SAFE_UNIQUE_TYPE_REALLOCATION) {
                return getCellInventory() instanceof BasicCellInventory;
            }
            return getStorageTarget().supportsCapability(capability);
        }

        @Override
        public final @Nullable ConfigInventory getConfigInventory() {
            var stack = getCellStack();
            if (stack.isEmpty() || !(stack.getItem() instanceof ICellWorkbenchItem workbenchItem)) {
                return null;
            }
            return workbenchItem.getConfigInventory(stack);
        }

        @Override
        public final @Nullable IUpgradeInventory getUpgradeInventory() {
            var stack = getCellStack();
            if (stack.isEmpty() || !(stack.getItem() instanceof ICellWorkbenchItem workbenchItem)) {
                return null;
            }
            return workbenchItem.getUpgrades(stack);
        }

        @Override
        public final CellTerminalContentSnapshot previewContent() {
            StorageCell cell = getCellInventory();
            return cell != null ? CellTerminalContentSnapshot.fromCounter(cell.getAvailableStacks())
                : CellTerminalContentSnapshot.fromCounter(new KeyCounter());
        }

        @Override
        public final CellTerminalPartitionSnapshot getPartitionSnapshot() {
            ConfigInventory config = getConfigInventory();
            return config != null ? CellTerminalWriteSupport.snapshotPartition(config)
                : new CellTerminalPartitionSnapshot(List.of());
        }

        @Override
        public final void setPartition(List<? extends @Nullable GenericStack> partitionSlots) {
            ConfigInventory config = requireLiveCellSlot().getConfigInventory();
            if (config == null) {
                CellTerminalWriteSupport.fail("Cell Terminal cell slot has no partition inventory. target=%s, slot=%d",
                    getStorageTarget().stableTargetId(), slotIndex());
            }
            CellTerminalWriteSupport.setPartition(config, partitionSlots);
            reloadLiveCellSlot();
        }

        @Override
        public final <T> T simulateWithPartition(List<? extends @Nullable GenericStack> partitionSlots,
                                                 PartitionSimulation<T> simulation) {
            Objects.requireNonNull(simulation, "simulation");
            CellTerminalCellSlotTarget liveSlot = requireLiveCellSlot();
            ConfigInventory config = liveSlot.getConfigInventory();
            if (config == null) {
                CellTerminalWriteSupport.fail("Cell Terminal cell slot has no partition inventory. target=%s, slot=%d",
                    getStorageTarget().stableTargetId(), slotIndex());
            }
            List<@Nullable GenericStack> baseline = CellTerminalWriteSupport.snapshotPartition(config).slots();
            AbstractCellSlotTarget nativeLiveSlot = requireNativeCellSlot(liveSlot);
            try {
                CellTerminalWriteSupport.setPartition(config, partitionSlots);
                nativeLiveSlot.rebuildCellInventoryCache();
                StorageCell cell = liveSlot.getCellInventory();
                if (cell == null) {
                    CellTerminalWriteSupport.fail("Cell Terminal cell slot has no storage inventory. target=%s, slot=%d",
                        getStorageTarget().stableTargetId(), slotIndex());
                }
                return simulation.run(cell);
            } finally {
                CellTerminalWriteSupport.setPartition(config, baseline);
                nativeLiveSlot.rebuildCellInventoryCache();
            }
        }

        @Override
        public final CellTerminalUpgradeSnapshot getUpgradeSnapshot() {
            IUpgradeInventory upgrades = getUpgradeInventory();
            return upgrades != null ? CellTerminalWriteSupport.snapshotUpgrades(upgrades)
                : new CellTerminalUpgradeSnapshot(List.of());
        }

        @Override
        public final void setUpgrades(List<ItemStack> upgradeStacks) {
            IUpgradeInventory upgrades = requireLiveCellSlot().getUpgradeInventory();
            if (upgrades == null) {
                CellTerminalWriteSupport.fail("Cell Terminal cell slot has no upgrade inventory. target=%s, slot=%d",
                    getStorageTarget().stableTargetId(), slotIndex());
            }
            CellTerminalWriteSupport.setUpgrades(upgrades, upgradeStacks);
            reloadLiveCellSlot();
        }

        @Override
        public final CellTerminalCellSlotMutation insertCell(ItemStack stack) {
            return CellTerminalWriteSupport.insertCell(requireLiveInventory(), slotIndex(), stack);
        }

        @Override
        public final CellTerminalCellSlotMutation ejectCell() {
            return CellTerminalWriteSupport.ejectCell(requireLiveInventory(), slotIndex());
        }

        @Override
        public final CellTerminalCellSlotMutation replaceCell(ItemStack stack) {
            return CellTerminalWriteSupport.replaceCell(requireLiveInventory(), slotIndex(), stack);
        }

        @Override
        public abstract CellState getCellState();

        @Nullable
        protected abstract StorageCell getMountedCellInventory();

        protected abstract InternalInventory getCellHostInventory();

        private CellTerminalCellSlotTarget requireLiveCellSlot() {
            CellTerminalStorageTarget liveTarget = requireResolvedStorageTarget(
                getStorageTarget().stableTargetId(),
                getStorageTarget().locator());
            List<? extends CellTerminalCellSlotTarget> liveSlots = liveTarget.getCellSlots();
            int slotIndex = slotIndex();
            if (slotIndex < 0 || slotIndex >= liveSlots.size()) {
                CellTerminalWriteSupport.fail(
                    "Cell Terminal cell slot index no longer exists. target=%s, slot=%d, size=%d",
                    getStorageTarget().stableTargetId(),
                    slotIndex,
                    liveSlots.size());
            }
            return liveSlots.get(slotIndex);
        }

        private InternalInventory requireLiveInventory() {
            CellTerminalCellSlotTarget liveSlot = requireLiveCellSlot();
            if (liveSlot instanceof AbstractCellSlotTarget nativeSlot) {
                return nativeSlot.getCellHostInventory();
            }
            CellTerminalWriteSupport.fail("Unsupported live cell slot implementation: %s",
                liveSlot.getClass().getName());
            throw new IllegalStateException("unreachable");
        }

        protected abstract void rebuildCellInventoryCache();

        private void reloadLiveCellSlot() {
            CellTerminalCellSlotTarget liveSlot = requireLiveCellSlot();
            StorageCell cell = liveSlot.getCellInventory();
            if (cell != null) {
                cell.persist();
            }
            if (liveSlot instanceof AbstractCellSlotTarget nativeSlot) {
                nativeSlot.invalidateMountedCell();
            } else {
                CellTerminalWriteSupport.fail("Unsupported live cell slot implementation: %s",
                    liveSlot.getClass().getName());
            }
        }

        protected abstract void invalidateMountedCell();
    }

    private static final class DriveCellSlotTarget extends AbstractCellSlotTarget {
        private final TileDrive drive;

        private DriveCellSlotTarget(CellTerminalStorageTarget storageTarget, TileDrive drive, int slotIndex) {
            super(storageTarget, slotIndex);
            this.drive = Objects.requireNonNull(drive, "drive");
        }

        @Override
        public ItemStack getCellStack() {
            return this.drive.getInternalInventory().getStackInSlot(slotIndex());
        }

        @Override
        public CellState getCellState() {
            return this.drive.getCellStatus(slotIndex());
        }

        @Override
        protected @Nullable StorageCell getMountedCellInventory() {
            return this.drive.getOriginalCellInventory(slotIndex());
        }

        @Override
        protected InternalInventory getCellHostInventory() {
            return this.drive.getInternalInventory();
        }

        @Override
        protected void invalidateMountedCell() {
            this.drive.invalidateCellInventory(slotIndex());
        }

        @Override
        protected void rebuildCellInventoryCache() {
            this.drive.rebuildCellInventoryCache(slotIndex());
        }
    }

    private static final class MEChestCellSlotTarget extends AbstractCellSlotTarget {
        private final TileMEChest chest;

        private MEChestCellSlotTarget(CellTerminalStorageTarget storageTarget, TileMEChest chest, int slotIndex) {
            super(storageTarget, slotIndex);
            this.chest = Objects.requireNonNull(chest, "chest");
        }

        @Override
        public ItemStack getCellStack() {
            return this.chest.getCell();
        }

        @Override
        public CellState getCellState() {
            return this.chest.getCellStatus(slotIndex());
        }

        @Override
        protected @Nullable StorageCell getMountedCellInventory() {
            return this.chest.getOriginalCellInventory(slotIndex());
        }

        @Override
        protected InternalInventory getCellHostInventory() {
            return this.chest.getInternalInventory().getSlotInv(1);
        }

        @Override
        protected void invalidateMountedCell() {
            this.chest.invalidateCellInventory(slotIndex());
        }

        @Override
        protected void rebuildCellInventoryCache() {
            this.chest.rebuildCellInventoryCache(slotIndex());
        }
    }
}
