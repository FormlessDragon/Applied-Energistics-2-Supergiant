package ae2.parts.p2p;

import ae2.api.AECapabilities;
import ae2.api.behaviors.GenericInternalInventory;
import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.implementations.blockentities.ICraftingMachine;
import ae2.api.implementations.blockentities.IPatternProviderBatchTarget;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.networking.security.IActionSource;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.core.AppEng;
import ae2.core.localization.GuiText;
import ae2.helpers.patternprovider.PatternProviderLogic;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.helpers.patternprovider.PatternProviderReturnInventory;
import ae2.helpers.patternprovider.PatternProviderTarget;
import ae2.items.parts.PartModels;
import ae2.text.TextComponentItemStack;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.DimensionManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class PatternProviderP2PTunnelPart extends P2PTunnelPart<PatternProviderP2PTunnelPart>
    implements ICraftingMachine {

    private static final String NEXT_OUTPUT_INDEX_TAG = "nextOutputIndex";
    private static final String LAST_INPUT_TAG = "lastInput";
    private static final String LAST_INPUT_DIM_TAG = "dim";
    private static final String LAST_INPUT_X_TAG = "x";
    private static final String LAST_INPUT_Y_TAG = "y";
    private static final String LAST_INPUT_Z_TAG = "z";
    private static final String LAST_INPUT_SIDE_TAG = "side";

    private static final P2PModels MODELS = new P2PModels(
        AppEng.makeId("part/p2p/p2p_tunnel_pattern_provider"),
        AppEng.makeId("part/p2p_tunnel_pattern_provider"));

    private final MEStorage inputStorageApi = new InputStorageApi();
    private final GenericInternalInventory returnInventoryApi = new ReturnInventoryApi();
    private int nextOutputIndex;
    @Nullable
    private TunnelIdentity lastInput;
    @Nullable
    private AEItemKey plannedPattern;
    @Nullable
    private PatternProviderP2PTunnelPart plannedOutput;

    public PatternProviderP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    static int normalizeOutputIndex(int index, int outputCount) {
        if (outputCount <= 0) {
            return 0;
        }
        return Math.floorMod(index, outputCount);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.nextOutputIndex = data.getInteger(NEXT_OUTPUT_INDEX_TAG);
        this.lastInput = readIdentity(data.getCompoundTag(LAST_INPUT_TAG));
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger(NEXT_OUTPUT_INDEX_TAG, this.nextOutputIndex);
        if (this.lastInput != null) {
            data.setTag(LAST_INPUT_TAG, this.lastInput.writeToTag());
        } else {
            data.removeTag(LAST_INPUT_TAG);
        }
    }

    @Nullable
    public ICraftingMachine getCraftingMachineApi() {
        return !isOutput() ? this : null;
    }

    @Nullable
    public GenericInternalInventory getReturnInventoryApi() {
        return isOutput() ? this.returnInventoryApi : null;
    }

    @Nullable
    public MEStorage getInputStorageApi() {
        return !isOutput() ? this.inputStorageApi : null;
    }

    public List<PatternProviderP2PTunnelPart> getOutputsInAttemptOrder() {
        return getOutputsInAttemptOrder(null);
    }

    public List<PatternProviderP2PTunnelPart> getOutputsInAttemptOrder(@Nullable PatternProviderP2PTunnelPart preferred) {
        ObjectList<PatternProviderP2PTunnelPart> outputs = new ObjectArrayList<>(getOutputs());
        int preferredIndex = preferred == null ? -1 : outputs.indexOf(preferred);
        int start = preferredIndex >= 0 ? preferredIndex : normalizeOutputIndex(this.nextOutputIndex, outputs.size());
        if (start == 0) {
            return outputs;
        }

        ObjectList<PatternProviderP2PTunnelPart> ordered = new ObjectArrayList<>(outputs.size());
        ordered.addAll(outputs.subList(start, outputs.size()));
        ordered.addAll(outputs.subList(0, start));
        return ordered;
    }

    public void planOutputForPattern(IPatternDetails patternDetails, PatternProviderP2PTunnelPart outputTunnel) {
        this.plannedPattern = patternDetails.getDefinition();
        this.plannedOutput = outputTunnel;
    }

    @Nullable
    public PatternProviderP2PTunnelPart consumePlannedOutput(IPatternDetails patternDetails) {
        PatternProviderP2PTunnelPart output = this.plannedOutput;
        if (this.plannedPattern == null || !this.plannedPattern.equals(patternDetails.getDefinition())) {
            output = null;
        }
        this.plannedPattern = null;
        this.plannedOutput = null;
        return output;
    }

    public void onRemotePushSucceeded(PatternProviderP2PTunnelPart outputTunnel) {
        List<PatternProviderP2PTunnelPart> outputs = getOutputs();
        int outputIndex = outputs.indexOf(outputTunnel);
        if (outputIndex >= 0) {
            this.nextOutputIndex = normalizeOutputIndex(outputIndex + 1, outputs.size());
            getHost().markForSave();
        }
        outputTunnel.rememberLastInput(this);
    }

    @Nullable
    public RemoteMachineTarget findRemoteMachineTarget() {
        if (!isOutput() || !isActive()) {
            return null;
        }

        TargetSide target = getTargetSide();
        if (target == null) {
            return null;
        }

        ICraftingMachine machine = ICraftingMachine.of(target.tileEntity(), target.side());
        if (machine == null || !machine.acceptsPlans()) {
            return null;
        }

        IPatternProviderBatchTarget batchTarget = machine;
        TileEntity blockEntity = target.tileEntity();
        if (blockEntity.hasCapability(AECapabilities.PATTERN_PROVIDER_BATCH_TARGET, target.side())) {
            IPatternProviderBatchTarget capabilityTarget = blockEntity.getCapability(
                AECapabilities.PATTERN_PROVIDER_BATCH_TARGET, target.side());
            if (capabilityTarget != null) {
                batchTarget = capabilityTarget;
            }
        }
        return new RemoteMachineTarget(machine, batchTarget, target.side());
    }

    @Nullable
    public PatternProviderTarget findRemoteExternalTarget(IActionSource actionSource) {
        if (!isOutput() || !isActive()) {
            return null;
        }

        TargetSide target = getTargetSide();
        if (target == null) {
            return null;
        }
        return PatternProviderTarget.get(getLevel(), target.pos(), target.side(), actionSource);
    }

    @Override
    @Nullable
    public PatternContainerGroup getCraftingMachineInfo() {
        Set<PatternContainerGroup> groups = new ObjectLinkedOpenHashSet<>();
        for (PatternProviderP2PTunnelPart output : getOutputsInAttemptOrder()) {
            TargetSide target = output.getTargetSide();
            if (target == null) {
                continue;
            }
            PatternContainerGroup group = PatternContainerGroup.fromMachine(output.getLevel(), target.pos(),
                target.side());
            if (group != null) {
                groups.add(group);
            }
        }
        return switch (groups.size()) {
            case 0 -> null;
            case 1 -> groups.iterator().next();
            default -> getMergedCraftingMachineInfo(groups);
        };
    }

    private PatternContainerGroup getMergedCraftingMachineInfo(Set<PatternContainerGroup> groups) {
        ObjectList<ITextComponent> tooltip = new ObjectArrayList<>();
        tooltip.add(GuiText.AdjacentToDifferentMachines.text()
                                                       .setStyle(new Style().setBold(true).setColor(TextFormatting.WHITE)));
        for (PatternContainerGroup group : groups) {
            tooltip.add(group.name().createCopy());
            for (ITextComponent line : group.tooltip()) {
                tooltip.add(new TextComponentString("  ").appendSibling(line.createCopy()));
            }
        }

        AEItemKey icon = AEItemKey.of(this.getPartItem().asItem());
        ITextComponent name = icon == null
            ? TextComponentItemStack.of(this.getPartItem().asItemStack())
            : icon.getDisplayName();
        return new PatternContainerGroup(icon, name, ObjectLists.unmodifiable(tooltip));
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, int multiplier,
                               EnumFacing ejectionDirection) {
        PatternProviderLogic source = findAdjacentPatternProviderLogic();
        return source != null && source.pushPatternThroughP2P(this, patternDetails, inputs, multiplier);
    }

    @Override
    public int getMaxPatternPushMultiplier(IPatternDetails patternDetails, KeyCounter[] inputs, int maxMultiplier,
                                           EnumFacing ejectionDirection) {
        PatternProviderLogic source = findAdjacentPatternProviderLogic();
        return source == null ? 0 : source.getMaxPatternPushMultiplierThroughP2P(this, patternDetails, inputs,
            maxMultiplier);
    }

    @Override
    public boolean acceptsPlans() {
        return !isOutput() && isActive();
    }

    @Override
    public void onTunnelNetworkChange() {
        super.onTunnelNetworkChange();
        notifyAdjacent();
    }

    @Override
    public void onTunnelConfigChange() {
        notifyAdjacent();
    }

    private void rememberLastInput(PatternProviderP2PTunnelPart input) {
        this.lastInput = TunnelIdentity.of(input);
        getHost().markForSave();
        notifyAdjacent();
    }

    @Nullable
    private PatternProviderLogic findAdjacentPatternProviderLogic() {
        EnumFacing side = getSide();
        if (side == null || getTileEntity() == null || getLevel() == null) {
            return null;
        }

        TileEntity adjacent = getLevel().getTileEntity(getTileEntity().getPos().offset(side));
        if (adjacent instanceof PatternProviderLogicHost host) {
            return host.getLogic();
        }
        if (adjacent instanceof IPartHost partHost) {
            IPart part = partHost.getPart(side.getOpposite());
            if (part instanceof PatternProviderLogicHost host) {
                return host.getLogic();
            }
        }
        return null;
    }

    @Nullable
    private PatternProviderReturnInventory findLastInputReturnInventory() {
        PatternProviderP2PTunnelPart input = resolveLastInput();
        return input == null ? null : input.findAdjacentReturnInventory();
    }

    @Nullable
    private PatternProviderReturnInventory findAdjacentReturnInventory() {
        PatternProviderLogic logic = findAdjacentPatternProviderLogic();
        return logic == null ? null : logic.getReturnInv();
    }

    @Nullable
    private PatternProviderP2PTunnelPart resolveLastInput() {
        if (this.lastInput == null) {
            return null;
        }

        var world = DimensionManager.getWorld(this.lastInput.dimension());
        if (world == null) {
            return null;
        }

        TileEntity tile = world.getTileEntity(this.lastInput.pos());
        if (!(tile instanceof IPartHost partHost)) {
            return null;
        }

        IPart part = partHost.getPart(this.lastInput.side());
        return part instanceof PatternProviderP2PTunnelPart tunnel && !tunnel.isOutput() ? tunnel : null;
    }

    @Nullable
    private TargetSide getTargetSide() {
        EnumFacing side = getSide();
        if (side == null || getTileEntity() == null || getLevel() == null) {
            return null;
        }

        BlockPos targetPos = getTileEntity().getPos().offset(side);
        TileEntity target = getLevel().getTileEntity(targetPos);
        return target == null ? null : new TargetSide(targetPos, target, side.getOpposite());
    }

    private void notifyAdjacent() {
        if (getTileEntity() != null && getLevel() != null) {
            Platform.notifyBlocksOfNeighbors(getLevel(), getTileEntity().getPos());
            getHost().markForUpdate();
        }
    }

    @Nullable
    private static TunnelIdentity readIdentity(NBTTagCompound tag) {
        if (tag.getKeySet().isEmpty() || !tag.hasKey(LAST_INPUT_SIDE_TAG)) {
            return null;
        }
        EnumFacing[] values = EnumFacing.VALUES;
        int sideOrdinal = tag.getInteger(LAST_INPUT_SIDE_TAG);
        if (sideOrdinal < 0 || sideOrdinal >= values.length) {
            return null;
        }
        return new TunnelIdentity(tag.getInteger(LAST_INPUT_DIM_TAG),
            new BlockPos(tag.getInteger(LAST_INPUT_X_TAG), tag.getInteger(LAST_INPUT_Y_TAG),
                tag.getInteger(LAST_INPUT_Z_TAG)),
            values[sideOrdinal]);
    }

    public record RemoteMachineTarget(ICraftingMachine machine, IPatternProviderBatchTarget batchTarget,
                                      EnumFacing ejectionDirection) {
    }

    private record TargetSide(BlockPos pos, TileEntity tileEntity, EnumFacing side) {
    }

    private record TunnelIdentity(int dimension, BlockPos pos, EnumFacing side) {
        static TunnelIdentity of(PatternProviderP2PTunnelPart tunnel) {
            return new TunnelIdentity(tunnel.getLevel().provider.getDimension(), tunnel.getTileEntity().getPos(),
                tunnel.getSide());
        }

        NBTTagCompound writeToTag() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger(LAST_INPUT_DIM_TAG, this.dimension);
            tag.setInteger(LAST_INPUT_X_TAG, this.pos.getX());
            tag.setInteger(LAST_INPUT_Y_TAG, this.pos.getY());
            tag.setInteger(LAST_INPUT_Z_TAG, this.pos.getZ());
            tag.setInteger(LAST_INPUT_SIDE_TAG, this.side.ordinal());
            return tag;
        }
    }

    private class InputStorageApi implements MEStorage {
        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (isOutput() || amount <= 0) {
                return 0;
            }

            for (PatternProviderP2PTunnelPart output : getOutputsInAttemptOrder()) {
                PatternProviderTarget target = output.findRemoteExternalTarget(source);
                if (target == null) {
                    continue;
                }

                long inserted = target.insert(what, amount, mode);
                if (inserted > 0) {
                    if (mode == Actionable.MODULATE) {
                        deductTransportCost(inserted, what.getType());
                        output.rememberLastInput(PatternProviderP2PTunnelPart.this);
                    }
                    return inserted;
                }
            }

            return 0;
        }

        @Override
        public ITextComponent getDescription() {
            return new TextComponentString(getDisplayName());
        }
    }

    private class ReturnInventoryApi implements GenericInternalInventory {
        @Nullable
        private PatternProviderReturnInventory delegate() {
            return findLastInputReturnInventory();
        }

        @Override
        public int size() {
            PatternProviderReturnInventory delegate = delegate();
            return delegate == null ? 0 : delegate.size();
        }

        @Override
        public @Nullable GenericStack getStack(int slot) {
            PatternProviderReturnInventory delegate = delegate();
            return delegate == null ? null : delegate.getStack(slot);
        }

        @Override
        public @Nullable AEKey getKey(int slot) {
            PatternProviderReturnInventory delegate = delegate();
            return delegate == null ? null : delegate.getKey(slot);
        }

        @Override
        public long getAmount(int slot) {
            PatternProviderReturnInventory delegate = delegate();
            return delegate == null ? 0 : delegate.getAmount(slot);
        }

        @Override
        public long getMaxAmount(AEKey key) {
            PatternProviderReturnInventory delegate = delegate();
            return delegate == null ? 0 : delegate.getMaxAmount(key);
        }

        @Override
        public long getCapacity(AEKeyType keyType) {
            PatternProviderReturnInventory delegate = delegate();
            return delegate == null ? 0 : delegate.getCapacity(keyType);
        }

        @Override
        public boolean canInsert() {
            PatternProviderReturnInventory delegate = delegate();
            return delegate != null && delegate.canInsert();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public void setStack(int slot, @Nullable GenericStack newStack) {
            PatternProviderReturnInventory delegate = delegate();
            if (delegate != null) {
                delegate.setStack(slot, newStack);
            }
        }

        @Override
        public boolean isSupportedType(AEKeyType type) {
            PatternProviderReturnInventory delegate = delegate();
            return delegate != null && delegate.isSupportedType(type);
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey what) {
            PatternProviderReturnInventory delegate = delegate();
            return delegate != null && delegate.isAllowedIn(slot, what);
        }

        @Override
        public long insert(int slot, AEKey what, long amount, Actionable mode) {
            PatternProviderReturnInventory delegate = delegate();
            return delegate == null ? 0 : delegate.insert(slot, what, amount, mode);
        }

        @Override
        public long extract(int slot, AEKey what, long amount, Actionable mode) {
            return 0;
        }

        @Override
        public void beginBatch() {
            PatternProviderReturnInventory delegate = delegate();
            if (delegate != null) {
                delegate.beginBatch();
            }
        }

        @Override
        public void endBatch() {
            PatternProviderReturnInventory delegate = delegate();
            if (delegate != null) {
                delegate.endBatch();
            }
        }

        @Override
        public void endBatchSuppressed() {
            PatternProviderReturnInventory delegate = delegate();
            if (delegate != null) {
                delegate.endBatchSuppressed();
            }
        }

        @Override
        public void onChange() {
            PatternProviderReturnInventory delegate = delegate();
            if (delegate != null) {
                delegate.onChange();
            }
        }
    }
}
