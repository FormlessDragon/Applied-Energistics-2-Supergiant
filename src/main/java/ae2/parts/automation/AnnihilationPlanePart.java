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

package ae2.parts.automation;

import ae2.api.behaviors.PickupStrategy;
import ae2.api.config.Actionable;
import ae2.api.config.FuzzyMode;
import ae2.api.config.IncludeExclude;
import ae2.api.config.Setting;
import ae2.api.config.Settings;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.StorageHelper;
import ae2.api.util.AECableType;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigManagerBuilder;
import ae2.container.GuiIds;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.core.gui.GuiOpener;
import ae2.core.settings.TickRates;
import ae2.helpers.IConfigInvHost;
import ae2.items.parts.PartModels;
import ae2.me.helpers.MachineSource;
import ae2.util.ConfigInventory;
import ae2.util.SettingsFrom;
import ae2.util.prioritylist.IPartitionList;
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
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AnnihilationPlanePart extends UpgradeablePart implements IGridTickable, IConfigInvHost {

    private static final String VANILLA_ENCHANTMENTS_TAG = "ench";
    private static final String PART_ENCHANTMENTS_TAG = "enchantments";
    private static final PlaneModels MODELS = new PlaneModels("part/annihilation_plane",
        "part/annihilation_plane_on");
    private final IActionSource actionSource = new MachineSource(this);
    private final PlaneConnectionHelper connectionHelper = new PlaneConnectionHelper(this);
    private IPartitionList filter;    private final ConfigInventory config = ConfigInventory.configTypes(63)
                                                          .supportedTypes(AEKeyType.items(), AEKeyType.fluids())
                                                          .changeListener(this::updateFilter)
                                                          .build();
    @Nullable
    protected List<PickupStrategy> pickupStrategies;
    private Object2IntMap<Enchantment> enchantments = new Object2IntLinkedOpenHashMap<>();
    private ContinuousGeneration continuousGeneration;
    private int continuousGenerationTicks;
    private IncludeExclude listMode = IncludeExclude.WHITELIST;

    static boolean shouldInsertWithFilter(boolean hasFilterEntries, boolean matchesFilter, boolean inverted) {
        if (!hasFilterEntries) {
            return true;
        }
        return inverted != matchesFilter;
    }

    public AnnihilationPlanePart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().addService(IGridTickable.class, this);
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

    static int getActiveConfigSlots(int capacityCards) {
        return Math.min(63, 18 + Math.max(0, capacityCards) * 9);
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void upgradesChanged() {
        this.updateFilter();
    }

    @Override
    protected void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        this.updateFilter();
    }

    private void updateFilter() {
        var builder = IPartitionList.builder();
        int slotsToUse = getActiveConfigSlots(getInstalledUpgrades(AEItems.CAPACITY_CARD));
        for (int slot = 0; slot < this.config.size() && slot < slotsToUse; slot++) {
            builder.add(this.config.getKey(slot));
        }
        if (isUpgradedWith(AEItems.FUZZY_CARD)) {
            builder.fuzzyMode(getConfigManager().getSetting(Settings.FUZZY_MODE));
        }
        this.filter = builder.build();
        this.listMode = isUpgradedWith(AEItems.INVERTER_CARD) ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        this.refresh();
    }

    private IPartitionList getFilter() {
        if (this.filter == null) {
            updateFilter();
        }
        return this.filter;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        this.enchantments = readEnchantmentsFromTag(data);
        this.config.readFromChildTag(data, "config");
        this.updateFilter();
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
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        if (!this.enchantments.isEmpty()) {
            data.setTag(PART_ENCHANTMENTS_TAG, writeEnchantments(this.enchantments));
        }
        this.config.writeToChildTag(data, "config");
    }

    @Override
    public void importSettings(SettingsFrom mode, NBTTagCompound data, @Nullable EntityPlayer player) {
        super.importSettings(mode, data, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            this.enchantments = readEnchantmentsFromTag(data);
        }
        this.pickupStrategies = null;
        this.updateFilter();
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!isClientSide()) {
            GuiOpener.openPartGui(player, GuiIds.GuiKey.ANNIHILATION_PLANE, this);
        }
        return true;
    }

    @Override
    public void exportSettings(SettingsFrom mode, NBTTagCompound data) {
        super.exportSettings(mode, data);
        if (mode == SettingsFrom.DISMANTLE_ITEM && !this.enchantments.isEmpty()) {
            data.setTag(PART_ENCHANTMENTS_TAG, writeEnchantments(this.enchantments));
            data.setTag(VANILLA_ENCHANTMENTS_TAG, writeEnchantments(this.enchantments));
        }
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (super.onUseItemOn(heldItem, player, hand, pos)) {
            return true;
        }
        return this.onUseWithoutItem(player, pos);
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
            UUID owner = node.getOwningPlayerProfileId();
            this.pickupStrategies = StackWorldBehaviors.createPickupStrategies((WorldServer) self.getWorld(),
                pos, side, self, this.enchantments, owner);
        }
        return this.pickupStrategies;
    }

    public Object2IntMap<Enchantment> getEnchantments() {
        return Object2IntMaps.unmodifiable(this.enchantments);
    }

    @Override
    public void onEntityCollision(Entity entity) {
        if (!entity.isEntityAlive() || isClientSide() || !this.getMainNode().isActive()) {
            return;
        }

        IGrid grid = getMainNode().getGrid();
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
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isActive()) {
            return TickRateModulation.SLEEP;
        }

        IGrid grid = node.grid();

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

    private long insertIntoGrid(AEKey what, long amount, Actionable mode) {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        if (!this.getFilter().matchesFilter(what, this.listMode)) {
            return 0;
        }
        long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
            what, amount, this.actionSource, mode);
        if (inserted < amount && isUpgradedWith(AEItems.VOID_CARD)) {
            return amount;
        }
        return inserted;
    }

    private void refresh() {
        for (PickupStrategy pickupStrategy : getPickupStrategies()) {
            pickupStrategy.reset();
        }

        getMainNode().ifPresent((g, n) -> g.getTickManager().alertDevice(n));
    }

    @Override
    public ConfigInventory getConfig() {
        return this.config;
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
