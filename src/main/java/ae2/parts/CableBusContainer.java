/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.parts;

import ae2.api.config.YesNo;
import ae2.api.implementations.items.IFacadeItem;
import ae2.api.implementations.parts.ICablePart;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.parts.IFacadeContainer;
import ae2.api.parts.IFacadePart;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartHost;
import ae2.api.parts.IPartItem;
import ae2.api.parts.SelectedPart;
import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.api.util.DimensionalBlockPos;
import ae2.client.render.cablebus.CableBusRenderState;
import ae2.client.render.cablebus.CableCoreType;
import ae2.client.render.cablebus.FacadeRenderState;
import ae2.core.AELog;
import ae2.core.AppEng;
import ae2.facade.FacadeContainer;
import ae2.me.InWorldGridNode;
import ae2.parts.networking.CablePart;
import ae2.tile.networking.TileCableBus;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class CableBusContainer implements IPartHost, ICableBusContainer {

    private static final String[] NBT_KEY_SIDES = new String[]{
        "down",
        "up",
        "north",
        "south",
        "west",
        "east",
        "cable"
    };
    private static final ThreadLocal<Boolean> IS_LOADING = new ThreadLocal<>();

    private final CableBusStorage storage = new CableBusStorage();
    private IPartHost host;
    private YesNo hasRedstone = YesNo.UNDECIDED;
    private boolean inWorld;
    private boolean requiresDynamicRender;
    private final IFacadeContainer facadeContainer = new FacadeContainer(this.storage, this::facadeChanged);

    public CableBusContainer(IPartHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public static boolean isLoading() {
        Boolean isLoading = IS_LOADING.get();
        return isLoading != null && isLoading;
    }

    public void setHost(IPartHost host) {
        this.host.clearContainer();
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    public IFacadeContainer getFacadeContainer() {
        return this.facadeContainer;
    }

    private void facadeChanged(EnumFacing side) {
        refreshAfterExternalStateChange();
        markForUpdate();
        markForSave();
        notifyNeighborNow(side);
    }

    @Nullable
    @Override
    public IPart getPart(@Nullable EnumFacing side) {
        return side == null ? this.storage.getCenter() : this.storage.getPart(side);
    }

    @Override
    public boolean canAddPart(ItemStack stack, @Nullable EnumFacing side) {
        if (stack.isEmpty()) {
            return false;
        }

        if (side != null && stack.getItem() instanceof IFacadeItem facadeItem) {
            return this.facadeContainer.canAddFacade(facadeItem.createPartFromItemStack(stack, side));
        }

        if (!(stack.getItem() instanceof IPartItem<?> partItem)) {
            return false;
        }

        IPart part = partItem.createPart();
        if (part == null) {
            return false;
        }

        if (part instanceof ICablePart cablePart) {
            return this.storage.getCenter() == null && arePartsCompatibleWithCable(cablePart);
        }

        return side != null && getPart(side) == null && isPartCompatibleWithCable(part, this.storage.getCenter());
    }

    @Nullable
    @Override
    public <T extends IPart> T addPart(IPartItem<T> partItem, @Nullable EnumFacing side, @Nullable EntityPlayer owner) {
        T part = partItem.createPart();
        if (part == null) {
            return null;
        }

        if (part instanceof ICablePart cablePart) {
            if (this.storage.getCenter() != null || !arePartsCompatibleWithCable(cablePart)) {
                return null;
            }

            this.storage.setCenter(cablePart);
            cablePart.setPartHostInfo(null, this, this.host.getTileEntity());

            if (owner != null) {
                cablePart.onPlacement(owner);
            }

            if (this.inWorld) {
                updateConnections();
                cablePart.addToWorld();
            }

            if (!connectCableToExistingParts(cablePart)) {
                removePartWithoutUpdates(null);
                return null;
            }
        } else {
            if (side == null || getPart(side) != null || !isPartCompatibleWithCable(part, this.storage.getCenter())) {
                return null;
            }

            this.storage.setPart(side, part);
            part.setPartHostInfo(side, this, this.host.getTileEntity());

            if (owner != null) {
                part.onPlacement(owner);
            }

            if (this.inWorld) {
                part.addToWorld();
            }

            if (!connectPartToCable(part)) {
                removePartWithoutUpdates(side);
                return null;
            }
        }

        updateAfterPartChange(side);
        return part;
    }

    @Nullable
    @Override
    public <T extends IPart> T replacePart(IPartItem<T> partItem, @Nullable EnumFacing side, @Nullable EntityPlayer owner,
                                           @Nullable EnumHand hand) {
        removePartWithoutUpdates(side);
        return addPart(partItem, side, owner);
    }

    @Override
    public void removePartFromSide(@Nullable EnumFacing side) {
        removePartWithoutUpdates(side);
        updateAfterPartChange(side);

        if (this.inWorld && isEmpty()) {
            cleanup();
        }
    }

    @Override
    public void markForUpdate() {
        this.host.markForUpdate();
    }

    @Override
    public DimensionalBlockPos getLocation() {
        return this.host.getLocation();
    }

    @Override
    public TileEntity getTileEntity() {
        return this.host.getTileEntity();
    }

    @Override
    public AEColor getColor() {
        return this.storage.getCenter() != null ? this.storage.getCenter().getCableColor() : AEColor.TRANSPARENT;
    }

    @Override
    public void clearContainer() {
        throw new UnsupportedOperationException("Now that is silly!");
    }

    @Override
    public boolean isBlocked(EnumFacing side) {
        return this.host.isBlocked(side);
    }

    @Override
    public SelectedPart selectPartLocal(Vec3d pos) {
        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(side);
            if (part == null) {
                continue;
            }

            ObjectList<AxisAlignedBB> boxes = new ObjectArrayList<>();
            part.getBoxes(new BusCollisionHelper(boxes, side, true));
            for (AxisAlignedBB box : boxes) {
                if (box.grow(0.002).contains(pos)) {
                    return new SelectedPart(part, side);
                }
            }
        }

        if (AppEng.instance().getCableRenderMode().opaqueFacades) {
            for (EnumFacing side : EnumFacing.VALUES) {
                IFacadePart facade = this.facadeContainer.getFacade(side);
                if (facade == null) {
                    continue;
                }

                ObjectList<AxisAlignedBB> boxes = new ObjectArrayList<>();
                facade.getBoxes(new BusCollisionHelper(boxes, side, true), true);
                for (AxisAlignedBB box : boxes) {
                    if (box.grow(0.01).contains(pos)) {
                        return new SelectedPart(facade, side);
                    }
                }
            }
        }

        return new SelectedPart();
    }

    @Override
    public boolean removePart(IPart part) {
        if (getPart(null) == part) {
            removePartFromSide(null);
            return true;
        }

        for (EnumFacing side : EnumFacing.VALUES) {
            if (getPart(side) == part) {
                removePartFromSide(side);
                return true;
            }
        }

        return false;
    }

    @Override
    public void markForSave() {
        this.host.markForSave();
    }

    @Override
    public void partChanged() {
        if (this.storage.getCenter() == null) {
            ObjectList<ItemStack> facades = new ObjectArrayList<>();
            for (EnumFacing side : EnumFacing.VALUES) {
                IFacadePart facade = this.facadeContainer.getFacade(side);
                if (facade != null) {
                    facades.add(facade.getItemStack());
                    this.facadeContainer.removeFacade(this, side);
                }
            }

            if (!facades.isEmpty()) {
                TileEntity tile = this.host.getTileEntity();
                Platform.spawnDrops(tile.getWorld(), tile.getPos(), facades);
            }
        }

        for (EnumFacing side : EnumFacing.VALUES) {
            IPart part = getPart(side);
            if (part != null && part.getExternalFacingNode() instanceof InWorldGridNode inWorldNode) {
                inWorldNode.setExposedOnSides(EnumSet.of(side));
            }
        }

        this.host.partChanged();
    }

    @Override
    public boolean hasRedstone() {
        if (this.hasRedstone == YesNo.UNDECIDED) {
            updateRedstone();
        }

        return this.hasRedstone == YesNo.YES;
    }

    @Override
    public boolean isEmpty() {
        if (!this.facadeContainer.isEmpty()) {
            return false;
        }

        if (this.storage.getCenter() != null) {
            return false;
        }

        for (EnumFacing side : EnumFacing.VALUES) {
            if (this.storage.getPart(side) != null) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void cleanup() {
        this.host.cleanup();
    }

    @Override
    public void notifyNeighbors() {
        this.host.notifyNeighbors();
    }

    @Override
    public void notifyNeighborNow(EnumFacing side) {
        this.host.notifyNeighborNow(side);
    }

    @Override
    public boolean isInWorld() {
        return this.inWorld;
    }

    @Override
    public int isProvidingStrongPower(EnumFacing side) {
        IPart part = getPart(side);
        return part != null ? part.isProvidingStrongPower() : 0;
    }

    @Override
    public int isProvidingWeakPower(EnumFacing side) {
        IPart part = getPart(side);
        return part != null ? part.isProvidingWeakPower() : 0;
    }

    @Override
    public boolean canConnectRedstone(EnumFacing side) {
        IPart part = getPart(side);
        return part != null && part.canConnectRedstone();
    }

    @Override
    public void onEntityCollision(Entity entity) {
        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(side);
            if (part != null) {
                part.onEntityCollision(entity);
            }
        }
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d localPos) {
        SelectedPart selectedPart = selectPartLocal(localPos);
        if (selectedPart.part != null) {
            return selectedPart.part.onUseItemOn(heldItem, player, hand, localPos);
        }

        if (selectedPart.facade != null && selectedPart.facade.onUseItemOn(heldItem, player, hand, localPos)) {
            markForSave();
            markForUpdate();
            return true;
        }

        return false;
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d localPos) {
        SelectedPart selectedPart = selectPartLocal(localPos);
        return selectedPart.part != null && selectedPart.part.onUseWithoutItem(player, localPos);
    }

    @Override
    public boolean onWrenched(EntityPlayer player, Vec3d localPos) {
        SelectedPart selectedPart = selectPartLocal(localPos);
        TileEntity tile = this.host.getTileEntity();
        if (tile == null || tile.getWorld() == null) {
            return false;
        }

        ObjectList<ItemStack> drops = new ObjectArrayList<>();
        if (selectedPart.part != null) {
            selectedPart.part.addPartDrop(drops, true);
            selectedPart.part.addAdditionalDrops(drops, true);
            selectedPart.part.clearContent();
            if (!this.removePart(selectedPart.part)) {
                return false;
            }
        } else if (selectedPart.facade != null && selectedPart.side != null) {
            drops.add(selectedPart.facade.getItemStack());
            this.facadeContainer.removeFacade(this.host, selectedPart.side);
        } else {
            return false;
        }

        if (!drops.isEmpty()) {
            Platform.spawnDrops(tile.getWorld(), tile.getPos(), drops);
        }
        return true;
    }

    @Override
    public boolean onClicked(EntityPlayer player, Vec3d localPos) {
        SelectedPart selectedPart = selectPartLocal(localPos);
        if (selectedPart.part != null) {
            if (player.isSneaking()) {
                return selectedPart.part.onShiftClicked(player, localPos);
            } else {
                return selectedPart.part.onClicked(player, localPos);
            }
        } else if (selectedPart.facade != null) {
            return selectedPart.facade.onClicked(player, localPos);
        }
        return false;
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        this.hasRedstone = YesNo.UNDECIDED;

        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(side);
            if (part != null) {
                part.onNeighborChanged(level, pos, neighbor);
            }
        }
    }

    @Override
    public void onUpdateShape(EnumFacing side) {
        for (EnumFacing partSide : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(partSide);
            if (part != null) {
                part.onUpdateShape(side);
            }
        }
    }

    @Override
    public boolean recolourBlock(EnumFacing side, AEColor colour, EntityPlayer who) {
        IPart cable = getPart(null);
        if (cable instanceof ICablePart cablePart) {
            return cablePart.changeColor(colour, who);
        }

        return false;
    }

    public boolean isSolidOnSide(@Nullable EnumFacing side) {
        if (side == null) {
            return false;
        }

        IFacadePart facade = this.facadeContainer.getFacade(side);
        if (facade != null) {
            return true;
        }

        IPart part = getPart(side);
        return part != null && part.isSolid();
    }

    @Override
    public boolean isLadder(EntityLivingBase entity) {
        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(side);
            if (part != null && part.isLadder(entity)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, Random random) {
        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(side);
            if (part != null) {
                part.animateTick(world, pos, random);
            }
        }
    }

    @Override
    public int getLightValue() {
        int light = 0;
        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(side);
            if (part != null) {
                light = Math.max(light, part.getLightLevel());
            }
        }
        return light;
    }

    @Override
    public boolean isRequiresDynamicRender() {
        return this.requiresDynamicRender;
    }

    @Override
    public Iterable<AxisAlignedBB> getBoxes(boolean includeFacades, @Nullable Entity entity, boolean visual) {
        ObjectList<AxisAlignedBB> boxes = new ObjectArrayList<>();

        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPartCollisionHelper helper = new BusCollisionHelper(boxes, side, visual);
            IPart part = getPart(side);
            if (part != null) {
                part.getBoxes(helper);
            }

            if (includeFacades && side != null && (AppEng.instance().getCableRenderMode().opaqueFacades || !visual)) {
                IFacadePart facade = this.facadeContainer.getFacade(side);
                if (facade != null) {
                    facade.getBoxes(helper, entity instanceof EntityItem);
                }
            }
        }

        return boxes;
    }

    public void writeToStream(PacketBuffer data) {
        int sides = 0;
        for (int i = 0; i < Platform.DIRECTIONS_WITH_NULL.length; i++) {
            if (getPart(Platform.DIRECTIONS_WITH_NULL[i]) != null) {
                sides |= 1 << i;
            }
        }

        data.writeByte(sides);

        for (int i = 0; i < Platform.DIRECTIONS_WITH_NULL.length; i++) {
            IPart part = getPart(Platform.DIRECTIONS_WITH_NULL[i]);
            if (part != null) {
                data.writeVarInt(IPartItem.getNetworkId(part.getPartItem()));
                part.writeToStream(data);
            }
        }

        this.facadeContainer.writeToStream(data);
    }

    public boolean readFromStream(PacketBuffer data) {
        int sides = data.readUnsignedByte();
        boolean changed = false;

        for (int i = 0; i < Platform.DIRECTIONS_WITH_NULL.length; i++) {
            EnumFacing side = Platform.DIRECTIONS_WITH_NULL[i];
            boolean present = (sides & (1 << i)) != 0;
            if (!present) {
                if (getPart(side) != null) {
                    removePartWithoutUpdates(side);
                    changed = true;
                }
                continue;
            }

            int itemId = data.readVarInt();
            IPartItem<?> partItem = IPartItem.byNetworkId(itemId);
            if (partItem == null) {
                throw new IllegalStateException("Invalid item from server for part: " + itemId);
            }

            IPart existingPart = getPart(side);
            if (existingPart != null && existingPart.getPartItem() == partItem) {
                changed |= existingPart.readFromStream(data);
                continue;
            }

            removePartWithoutUpdates(side);
            IPart newPart = addPart(partItem, side, null);
            if (newPart == null) {
                throw new IllegalStateException("Invalid stream for cable bus container.");
            }

            changed = true;
            changed |= newPart.readFromStream(data);
        }

        changed |= this.facadeContainer.readFromStream(data);
        refreshAfterExternalStateChange();
        return changed;
    }

    public void writeToNBT(NBTTagCompound data) {
        data.setInteger("hasRedstone", this.hasRedstone.ordinal());
        this.facadeContainer.writeToNBT(data);

        for (int i = 0; i < Platform.DIRECTIONS_WITH_NULL.length; i++) {
            EnumFacing side = Platform.DIRECTIONS_WITH_NULL[i];
            IPart part = getPart(side);
            if (part == null) {
                continue;
            }

            NBTTagCompound partData = new NBTTagCompound();
            NBTTagCompound visualData = new NBTTagCompound();
            part.writeVisualStateToNBT(visualData);
            if (!Platform.isNbtEmpty(visualData)) {
                partData.setTag("visual", visualData);
            }

            part.writeToNBT(partData);
            ResourceLocation id = IPartItem.getId(part.getPartItem());
            partData.setString("id", id.toString());
            data.setTag(NBT_KEY_SIDES[i], partData);
        }
    }

    public void readFromNBT(NBTTagCompound data) {
        IS_LOADING.set(true);
        try {
            if (data.hasKey("hasRedstone", NBT.TAG_INT)) {
                int redstoneState = data.getInteger("hasRedstone");
                YesNo[] states = YesNo.values();
                this.hasRedstone = redstoneState >= 0 && redstoneState < states.length
                    ? states[redstoneState]
                    : YesNo.UNDECIDED;
            }

            this.facadeContainer.readFromNBT(data);

            for (int i = 0; i < Platform.DIRECTIONS_WITH_NULL.length; i++) {
                EnumFacing side = Platform.DIRECTIONS_WITH_NULL[i];
                String key = NBT_KEY_SIDES[i];
                if (!data.hasKey(key, 10)) {
                    removePartWithoutUpdates(side);
                    continue;
                }

                NBTTagCompound partData = data.getCompoundTag(key);
                ResourceLocation itemId;
                try {
                    itemId = new ResourceLocation(partData.getString("id"));
                } catch (RuntimeException e) {
                    AELog.warn("Ignoring persisted part with invalid id {}", partData.getString("id"));
                    removePartWithoutUpdates(side);
                    continue;
                }
                IPartItem<?> partItem = IPartItem.byId(itemId);
                if (partItem == null) {
                    AELog.warn("Ignoring persisted part with non-part-item {}", itemId);
                    removePartWithoutUpdates(side);
                    continue;
                }

                IPart existingPart = getPart(side);
                if (existingPart == null || existingPart.getPartItem() != partItem) {
                    removePartWithoutUpdates(side);
                    existingPart = addPart(partItem, side, null);
                }

                if (existingPart != null) {
                    existingPart.readFromNBT(partData);
                } else {
                    AELog.warn("Invalid NBT for cable bus container: {} is not a valid part; it was ignored.", itemId);
                }
            }

            refreshAfterExternalStateChange();
        } finally {
            IS_LOADING.set(false);
        }
    }

    public void addPartDrops(List<ItemStack> drops) {
        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(side);
            if (part != null) {
                part.addPartDrop(drops, false);
            }

            if (side != null) {
                IFacadePart facade = this.facadeContainer.getFacade(side);
                if (facade != null) {
                    drops.add(facade.getItemStack());
                }
            }
        }
    }

    public void addAdditionalDrops(List<ItemStack> drops) {
        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(side);
            if (part != null) {
                part.addAdditionalDrops(drops, false);
            }
        }
    }

    public void clearContent() {
        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(side);
            if (part != null) {
                part.clearContent();
            }
        }
    }

    public void addToWorld() {
        if (this.inWorld) {
            return;
        }

        this.inWorld = true;
        IS_LOADING.set(true);

        try {
            TileEntity tile = this.host.getTileEntity();
            for (int i = Platform.DIRECTIONS_WITH_NULL.length - 1; i >= 0; i--) {
                EnumFacing side = Platform.DIRECTIONS_WITH_NULL[i];
                IPart part = getPart(side);
                if (part == null) {
                    continue;
                }

                part.setPartHostInfo(side, this, tile);
                part.addToWorld();

                if (side != null && this.storage.getCenter() != null) {
                    IGridNode sideNode = part.getGridNode();
                    IGridNode centerNode = this.storage.getCenter().getGridNode();
                    if (sideNode != null && centerNode != null) {
                        try {
                            GridHelper.createConnection(centerNode, sideNode);
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
            }

            updateConnections();
            markForUpdate();
            partChanged();
        } finally {
            IS_LOADING.set(false);
        }
    }

    public void removeFromWorld() {
        if (!this.inWorld) {
            return;
        }

        this.inWorld = false;
        for (EnumFacing side : Platform.DIRECTIONS_WITH_NULL) {
            IPart part = getPart(side);
            if (part != null) {
                part.removeFromWorld();
            }
        }
    }

    @Nullable
    public IGridNode getGridNode(@Nullable EnumFacing side) {
        IPart part = getPart(side);
        if (part != null) {
            IGridNode node = part.getExternalFacingNode();
            if (node != null) {
                return node;
            }
        }

        return this.storage.getCenter() != null ? this.storage.getCenter().getGridNode() : null;
    }

    public AECableType getCableConnectionType(@Nullable EnumFacing side) {
        IPart part = getPart(side);
        if (part != null) {
            return part.getExternalCableConnectionType();
        }

        return this.storage.getCenter() != null ? this.storage.getCenter().getCableConnectionType() : AECableType.NONE;
    }

    public float getCableConnectionLength(AECableType cable) {
        IPart center = getPart(null);
        return center != null ? center.getCableConnectionLength(cable) : -1;
    }

    public CableBusRenderState getRenderState() {
        final CablePart cable = (CablePart) this.storage.getCenter();
        final CableBusRenderState renderState = new CableBusRenderState();

        if (cable != null) {
            renderState.setCableColor(cable.getCableColor());
            renderState.setCableType(cable.getCableConnectionType());
            renderState.setCoreType(CableCoreType.fromCableType(cable.getCableConnectionType()));

            for (EnumFacing side : EnumFacing.VALUES) {
                if (!cable.isConnected(side)) {
                    continue;
                }

                AECableType connectionType = cable.getCableConnectionType();
                TileEntity tile = this.host.getTileEntity();
                IInWorldGridNodeHost adjacentHost = tile != null && tile.getWorld() != null
                    ? GridHelper.getNodeHost(tile.getWorld(), tile.getPos().offset(side))
                    : null;

                if (adjacentHost != null) {
                    connectionType = AECableType.min(connectionType, adjacentHost.getCableConnectionType(side.getOpposite()));
                }

                if (adjacentHost instanceof TileCableBus) {
                    renderState.getCableBusAdjacent().add(side);
                }

                renderState.getConnectionTypes().put(side, connectionType);
            }

            for (EnumFacing side : EnumFacing.VALUES) {
                int channels = cable.getCableConnectionType().isSmart() ? cable.getChannelsOnSide(side) : 0;
                renderState.getChannelsOnSide().put(side, channels);
            }
        }

        for (EnumFacing side : EnumFacing.VALUES) {
            final FacadeRenderState facadeState = this.getFacadeRenderState(side);
            if (facadeState != null) {
                renderState.getFacades().put(side, facadeState);
            }

            final IPart part = this.getPart(side);
            if (part == null) {
                continue;
            }

            Object partModelData = part.getModelData();
            if (partModelData != null) {
                renderState.getPartModelData().put(side, partModelData);
                if (partModelData instanceof Long) {
                    renderState.getPartFlags().put(side, ((Long) partModelData).longValue());
                } else if (partModelData instanceof Number) {
                    renderState.getAttachmentSpins().put(side, ((Number) partModelData).intValue());
                }
            }

            final IPartCollisionHelper bch = new BusCollisionHelper(renderState.getBoundingBoxes(), side, true);
            part.getBoxes(bch);

            AECableType desiredType = part.getDesiredConnectionType();
            if (renderState.getCoreType() == CableCoreType.GLASS
                && (desiredType == AECableType.SMART || desiredType == AECableType.COVERED)) {
                renderState.setCoreType(CableCoreType.COVERED);
            }

            int length = (int) part.getCableConnectionLength(null);
            if (length > 0 && length <= 8) {
                renderState.getAttachmentConnections().put(side, length);
            }

            renderState.getAttachments().put(side, part.getStaticModels());
        }

        return renderState;
    }

    public Iterable<AxisAlignedBB> getCollisionShape(@Nullable Entity entity) {
        return getBoxes(true, entity, false);
    }

    public void updateConnections() {
        TileEntity tile = this.host.getTileEntity();
        if (tile == null || tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }

        ICablePart center = this.storage.getCenter();
        if (center == null) {
            return;
        }

        EnumSet<EnumFacing> sides = EnumSet.allOf(EnumFacing.class);
        for (EnumFacing side : EnumFacing.VALUES) {
            if (getPart(side) != null || isBlocked(side)) {
                sides.remove(side);
            }
        }

        center.setExposedOnSides(sides);
    }

    private void updateDynamicRender() {
        this.requiresDynamicRender = false;

        for (EnumFacing side : EnumFacing.VALUES) {
            IPart part = getPart(side);
            if (part != null && part.requireDynamicRender()) {
                this.requiresDynamicRender = true;
                return;
            }
        }
    }

    private boolean arePartsCompatibleWithCable(ICablePart cablePart) {
        for (EnumFacing side : EnumFacing.VALUES) {
            IPart part = getPart(side);
            if (part != null && !isPartCompatibleWithCable(part, cablePart)) {
                return false;
            }
        }

        return true;
    }

    private boolean isPartCompatibleWithCable(IPart part, @Nullable ICablePart cablePart) {
        return cablePart == null || part.canBePlacedOn(cablePart.supportsBuses());
    }

    private boolean connectCableToExistingParts(ICablePart cablePart) {
        IGridNode cableNode = cablePart.getGridNode();
        if (cableNode == null) {
            return true;
        }

        for (EnumFacing side : EnumFacing.VALUES) {
            IPart part = getPart(side);
            if (part == null) {
                continue;
            }

            IGridNode partNode = part.getGridNode();
            if (partNode == null) {
                continue;
            }

            try {
                GridHelper.createConnection(cableNode, partNode);
            } catch (RuntimeException e) {
                return false;
            }
        }

        return true;
    }

    private boolean connectPartToCable(IPart part) {
        ICablePart cablePart = this.storage.getCenter();
        if (cablePart == null) {
            return true;
        }

        IGridNode cableNode = cablePart.getGridNode();
        IGridNode partNode = part.getGridNode();
        if (cableNode == null || partNode == null) {
            return true;
        }

        try {
            GridHelper.createConnection(cableNode, partNode);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void updateAfterPartChange(@Nullable EnumFacing side) {
        refreshAfterExternalStateChange();
        markForUpdate();
        markForSave();
        partChanged();

        if (side != null) {
            notifyNeighborNow(side);
        } else {
            notifyNeighbors();
        }
    }

    private void refreshAfterExternalStateChange() {
        updateDynamicRender();
        updateConnections();
    }

    private void removePartWithoutUpdates(@Nullable EnumFacing side) {
        if (side == null) {
            if (this.storage.getCenter() != null) {
                this.storage.getCenter().removeFromWorld();
            }
            this.storage.setCenter(null);
            return;
        }

        IPart part = getPart(side);
        if (part != null) {
            part.removeFromWorld();
        }
        this.storage.removePart(side);
    }

    private void updateRedstone() {
        TileEntity tile = this.host.getTileEntity();
        this.hasRedstone = tile.getWorld().getRedstonePowerFromNeighbors(tile.getPos()) > 0 ? YesNo.YES : YesNo.NO;
    }

    private @Nullable FacadeRenderState getFacadeRenderState(EnumFacing side) {
        final IFacadePart facade = this.facadeContainer.getFacade(side);
        if (facade != null) {
            IBlockState blockState = facade.getBlockState();
            if (blockState != null) {
                return new FacadeRenderState(blockState, !blockState.isOpaqueCube());
            }
        }

        return null;
    }
}
