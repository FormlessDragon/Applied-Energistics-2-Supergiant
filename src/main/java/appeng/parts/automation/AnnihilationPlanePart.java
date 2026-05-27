/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.parts.automation;

import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import appeng.api.util.AECableType;
import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.items.parts.PartModels;
import appeng.me.helpers.MachineSource;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AnnihilationPlanePart extends AEBasePart implements IGridTickable {

    private static final String VANILLA_ENCHANTMENTS_TAG = "ench";
    private static final String PART_ENCHANTMENTS_TAG = "enchantments";
    private static final PlaneModels MODELS = new PlaneModels("part/annihilation_plane",
        "part/annihilation_plane_on");
    private final IActionSource actionSource = new MachineSource(this);
    private final PlaneConnectionHelper connectionHelper = new PlaneConnectionHelper(this);
    @Nullable
    protected List<PickupStrategy> pickupStrategies;
    private Object2IntMap<Enchantment> enchantments = new Object2IntLinkedOpenHashMap<>();
    private ContinuousGeneration continuousGeneration;
    private int continuousGenerationTicks;

    public AnnihilationPlanePart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().addService(IGridTickable.class, this);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    private static Object2IntMap<Enchantment> readEnchantments(NBTTagList enchantmentsTag) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.setTagInfo("ench", enchantmentsTag.copy());
        return new Object2IntLinkedOpenHashMap<>(EnchantmentHelper.getEnchantments(stack));
    }

    private static NBTTagList writeEnchantments(Object2IntMap<Enchantment> enchantments) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        EnchantmentHelper.setEnchantments(enchantments, stack);
        return stack.getEnchantmentTagList().copy();
    }

    private static Object2IntMap<Enchantment> readEnchantmentsFromTag(NBTTagCompound data) {
        if (data.hasKey(PART_ENCHANTMENTS_TAG, 9)) {
            return readEnchantments(data.getTagList(PART_ENCHANTMENTS_TAG, 10));
        }
        if (data.hasKey(VANILLA_ENCHANTMENTS_TAG, 9)) {
            return readEnchantments(data.getTagList(VANILLA_ENCHANTMENTS_TAG, 10));
        }
        return new Object2IntLinkedOpenHashMap<>();
    }

    @Override
    public void addToWorld() {
        super.addToWorld();

        TileEntity host = getTileEntity();
        int buildHeight = host.getWorld().getActualHeight();

        this.continuousGenerationTicks = 0;
        this.continuousGeneration = null;
        if (AEConfig.instance().isAnnihilationPlaneSkyDustGenerationEnabled()
            && host.getPos().getY() + 1 >= buildHeight && getSide() == EnumFacing.UP) {
            this.continuousGeneration = new ContinuousGeneration(AEItemKey.of(AEItems.SKY_DUST.asItem()), 1, 200);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        this.enchantments = readEnchantmentsFromTag(data);
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        if (!this.enchantments.isEmpty()) {
            data.setTag(PART_ENCHANTMENTS_TAG, writeEnchantments(this.enchantments));
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, NBTTagCompound data, @Nullable EntityPlayer player) {
        super.importSettings(mode, data, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            this.enchantments = readEnchantmentsFromTag(data);
        }
        this.pickupStrategies = null;
    }

    @Override
    public void exportSettings(SettingsFrom mode, NBTTagCompound data) {
        super.exportSettings(mode, data);
        if (mode == SettingsFrom.DISMANTLE_ITEM && !this.enchantments.isEmpty()) {
            data.setTag(PART_ENCHANTMENTS_TAG, writeEnchantments(this.enchantments));
            data.setTag(VANILLA_ENCHANTMENTS_TAG, writeEnchantments(this.enchantments));
        }
    }

    public Object2IntMap<Enchantment> getEnchantments() {
        return Object2IntMaps.unmodifiable(this.enchantments);
    }

    protected List<PickupStrategy> getPickupStrategies() {
        if (this.pickupStrategies == null) {
            IGridNode node = getMainNode().getNode();
            if (node == null) {
                return Collections.emptyList();
            }
            EnumFacing partSide = this.getSide();
            if (partSide == null) {
                return Collections.emptyList();
            }

            TileEntity self = this.getHost().getTileEntity();
            BlockPos pos = self.getPos().offset(partSide);
            EnumFacing side = partSide.getOpposite();
            java.util.UUID owner = node.getOwningPlayerProfileId();
            this.pickupStrategies = StackWorldBehaviors.createPickupStrategies((WorldServer) self.getWorld(),
                pos, side, self, this.enchantments, owner);
        }
        return this.pickupStrategies;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        if (bch.isBBCollision()) {
            bch.addBox(0, 0, 14, 16, 16, 15.5);
            return;
        }

        this.connectionHelper.getBoxes(bch);
    }

    public PlaneConnections getConnections() {
        return this.connectionHelper.getConnections();
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        if (pos.offset(this.getSide()).equals(neighbor) && !isClientSide()) {
            this.refresh();
        }
    }

    @Override
    public void onUpdateShape(EnumFacing side) {
        EnumFacing ourSide = getSide();
        if (ourSide == null) {
            return;
        }
        if (side == ourSide) {
            if (!isClientSide()) {
                this.refresh();
            }
        } else if (ourSide.getAxis() != side.getAxis()) {
            this.connectionHelper.updateConnections();
        }
    }

    @Override
    public void onEntityCollision(Entity entity) {
        if (!entity.isEntityAlive() || isClientSide() || !this.getMainNode().isActive()) {
            return;
        }

        appeng.api.networking.IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }

        PickupStrategy strategy = null;
        for (PickupStrategy pickupStrategy : getPickupStrategies()) {
            if (pickupStrategy.canPickUpEntity(entity)) {
                strategy = pickupStrategy;
                break;
            }
        }
        if (strategy == null) {
            return;
        }

        BlockPos pos = getHost().getTileEntity().getPos();
        double planePosX = pos.getX();
        double planePosY = pos.getY();
        double planePosZ = pos.getZ();

        double posYMiddle = (entity.getEntityBoundingBox().minY + entity.getEntityBoundingBox().maxY) / 2.0D;
        double entityPosX = entity.posX;
        double entityPosY = entity.posY;
        double entityPosZ = entity.posZ;

        boolean captureX = entityPosX > planePosX && entityPosX < planePosX + 1;
        boolean captureY = posYMiddle > planePosY && posYMiddle < planePosY + 1;
        boolean captureZ = entityPosZ > planePosZ && entityPosZ < planePosZ + 1;

        EnumFacing side = getSide();
        if (side == null) {
            return;
        }

        boolean capture = switch (side) {
            case DOWN -> captureX && captureZ && entityPosY < planePosY + 0.1;
            case UP -> captureX && captureZ && entityPosY > planePosY + 0.9;
            case SOUTH -> captureX && captureY && entityPosZ > planePosZ + 0.9;
            case NORTH -> captureX && captureY && entityPosZ < planePosZ + 0.1;
            case EAST -> captureZ && captureY && entityPosX > planePosX + 0.9;
            case WEST -> captureZ && captureY && entityPosX < planePosX + 0.1;
        };

        if (capture && !strategy.pickUpEntity(grid.getEnergyService(), this::insertIntoGrid, entity)) {
            getMainNode().ifPresent((g, n) -> g.getTickManager().alertDevice(n));
        }
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 1;
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);

        if (getMainNode().hasGridBooted()) {
            this.refresh();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.AnnihilationPlane, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isActive()) {
            return TickRateModulation.SLEEP;
        }

        appeng.api.networking.IGrid grid = node.grid();

        if (this.continuousGeneration != null) {
            this.continuousGenerationTicks += ticksSinceLastCall;
            if (this.continuousGenerationTicks >= this.continuousGeneration.ticks()) {
                long amount = this.continuousGenerationTicks / this.continuousGeneration.ticks();
                insertIntoGrid(this.continuousGeneration.what(), amount * this.continuousGeneration.amount(),
                    Actionable.MODULATE);
                this.continuousGenerationTicks = (int) (this.continuousGenerationTicks
                    - amount * this.continuousGeneration.ticks());
            }
            return TickRateModulation.IDLE;
        }

        for (PickupStrategy pickupStrategy : getPickupStrategies()) {
            pickupStrategy.reset();
        }

        for (PickupStrategy pickupStrategy : getPickupStrategies()) {
            PickupStrategy.Result pickupResult = pickupStrategy.tryPickup(grid.getEnergyService(), this::insertIntoGrid);

            if (pickupResult == PickupStrategy.Result.PICKED_UP) {
                return TickRateModulation.URGENT;
            } else if (pickupResult == PickupStrategy.Result.CANT_STORE) {
                return TickRateModulation.IDLE;
            }
        }

        return TickRateModulation.SLEEP;
    }

    private void refresh() {
        for (PickupStrategy pickupStrategy : getPickupStrategies()) {
            pickupStrategy.reset();
        }

        getMainNode().ifPresent((g, n) -> g.getTickManager().alertDevice(n));
    }

    private long insertIntoGrid(AEKey what, long amount, Actionable mode) {
        appeng.api.networking.IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        return StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
            what, amount, this.actionSource, mode);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    @Override
    public Object getModelData() {
        return this.getConnections();
    }

    private record ContinuousGeneration(AEKey what, long amount, int ticks) {
    }
}
