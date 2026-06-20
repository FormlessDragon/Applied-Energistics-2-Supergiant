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

package ae2.block.crafting;

import ae2.api.crafting.cpu.ICraftingUnitDefinition;
import ae2.block.AEBaseTileBlock;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.localization.PlayerMessages;
import ae2.core.registries.CraftingUnitTransformationRegistry;
import ae2.helpers.crafting.CraftingCubeState;
import ae2.tile.AEBaseTile;
import ae2.tile.crafting.ICraftingCPUTileEntity;
import ae2.util.InteractionUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import java.util.EnumSet;
import java.util.Objects;

public abstract class AbstractCraftingUnitBlock<T extends AEBaseTile & ICraftingCPUTileEntity>
    extends AEBaseTileBlock<T> implements ICraftingUnitBlock {
    public static final PropertyBool FORMED = PropertyBool.create("formed");
    public static final PropertyBool POWERED = PropertyBool.create("powered");
    public static final IUnlistedProperty<CraftingCubeState> STATE = new IUnlistedProperty<>() {
        @Override
        public String getName() {
            return "state";
        }

        @Override
        public boolean isValid(CraftingCubeState value) {
            return true;
        }

        @Override
        public Class<CraftingCubeState> getType() {
            return CraftingCubeState.class;
        }

        @Override
        public String valueToString(CraftingCubeState value) {
            return String.valueOf(value);
        }
    };

    public final ICraftingUnitDefinition definition;
    public final ICraftingUnitType type;

    protected AbstractCraftingUnitBlock(ICraftingUnitType type, Class<T> tileEntityClass) {
        this((ICraftingUnitDefinition) type, tileEntityClass);
    }

    protected AbstractCraftingUnitBlock(ICraftingUnitDefinition definition, Class<T> tileEntityClass) {
        super(Material.IRON);
        this.definition = Objects.requireNonNull(definition, "definition");
        this.type = CraftingUnitTypeAdapter.wrap(definition);
        this.setHardness(2.2F);
        this.setResistance(11.0F);
        this.setTileEntity(tileEntityClass);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FORMED, Boolean.FALSE)
                                            .withProperty(POWERED, Boolean.FALSE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        ObjectList<IProperty<?>> properties = new ObjectArrayList<>(getOrientationStrategy().getProperties());
        properties.add(POWERED);
        properties.add(FORMED);
        return new ExtendedBlockState(this, properties.toArray(new IProperty<?>[0]), getUnlistedProperties());
    }

    protected IUnlistedProperty<?>[] getUnlistedProperties() {
        return new IUnlistedProperty<?>[]{FORWARD, UP, STATE};
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        state = super.getActualState(state, world, pos);

        T tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return state;
        }

        ICraftingCPUTileEntity.ClientState renderState = tile.getRenderState();
        return state.withProperty(FORMED, renderState.formed())
                    .withProperty(POWERED, renderState.powered());
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        state = super.getExtendedState(state, world, pos);
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }

        T tile = this.getTileEntity(world, pos);
        if (tile != null) {
            return ((IExtendedBlockState) state).withProperty(STATE,
                new CraftingCubeState(tile.getRenderState().connections()));
        }

        EnumSet<EnumFacing> connections = EnumSet.noneOf(EnumFacing.class);
        for (EnumFacing facing : EnumFacing.values()) {
            if (this.isConnected(world, pos, facing)) {
                connections.add(facing);
            }
        }
        return ((IExtendedBlockState) state).withProperty(STATE, new CraftingCubeState(connections));
    }

    private boolean isConnected(IBlockAccess world, BlockPos pos, EnumFacing side) {
        BlockPos otherPos = pos.offset(side);
        IBlockState selfState = world.getBlockState(pos);
        IBlockState otherState = world.getBlockState(otherPos);
        return otherState.getBlock() instanceof ICraftingUnitBlock
            && this.isCompatibleCraftingUnit(selfState, world, pos, otherState, otherPos);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = 0;
        if (state.getValue(POWERED)) {
            meta |= 1;
        }
        if (state.getValue(FORMED)) {
            meta |= 2;
        }
        return meta;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState()
                   .withProperty(POWERED, (meta & 1) == 1)
                   .withProperty(FORMED, (meta & 2) == 2);
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, T tileEntity) {
        return currentState.withProperty(POWERED, tileEntity.isPowered()).withProperty(FORMED, tileEntity.isFormed());
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        T tile = this.getTileEntity(world, pos);
        if (tile != null) {
            tile.updateMultiBlock(fromPos);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        T tile = this.getTileEntity(world, pos);
        if (tile != null) {
            tile.breakCluster();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, side, hitX, hitY, hitZ)) {
            return true;
        }

        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem.isEmpty()) {
            if (InteractionUtil.isInAlternateUseMode(player)) {
                Block craftingUnitBlock = Objects.requireNonNull(
                    CraftingUnitTransformationRegistry.getInstance().getBaseBlock(this));
                return this.removeUpgrade(world, pos, player, craftingUnitBlock.getDefaultState())
                    != EnumActionResult.PASS;
            }
        } else if (this.upgrade(world, pos, state, player, side, heldItem)) {
            return true;
        }

        T tile = this.getTileEntity(world, pos);
        if (tile != null && tile.isFormed() && tile.isActive()) {
            if (!world.isRemote) {
                GuiOpener.openGui(player, GuiIds.GuiKey.CRAFTING_CPU, tile);
            }
            return true;
        }

        return false;
    }

    private static void giveOrDrop(EntityPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ItemStack returnedStack = stack.copy();
        if (!player.inventory.addItemStackToInventory(returnedStack)) {
            player.dropItem(returnedStack, false);
        }
    }

    private boolean upgrade(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumFacing side,
                            ItemStack heldItem) {
        Block upgradedBlock = CraftingUnitTransformationRegistry.getInstance().findUpgrade(this, heldItem);
        if (!(upgradedBlock instanceof AbstractCraftingUnitBlock<?> craftingBlock) || upgradedBlock == state.getBlock()) {
            return false;
        }

        T tile = this.getTileEntity(world, pos);
        if (tile != null && tile.getCluster() != null && tile.getCluster().isBusy()) {
            if (!world.isRemote) {
                player.sendStatusMessage(PlayerMessages.CraftingCpuBusy.text(), true);
            }
            return true;
        }

        if (world.isRemote) {
            return true;
        }

        ItemStack removedUpgrade = CraftingUnitTransformationRegistry.getInstance().getRemovedUpgrade(this);
        IBlockState newState = craftingBlock.getDefaultState();
        if (!craftingBlock.getOrientationStrategy().getProperties().isEmpty()) {
            newState = craftingBlock.getOrientationStrategy().setFacing(newState, side);
        }

        if (this.transform(world, pos, newState)) {
            heldItem.shrink(1);
            giveOrDrop(player, removedUpgrade);
            return true;
        }
        return false;
    }

    private EnumActionResult removeUpgrade(World world, BlockPos pos, EntityPlayer player, IBlockState newState) {
        if (Objects.equals(this, CraftingUnitTransformationRegistry.getInstance().getBaseBlock(this)) || world.isRemote) {
            return EnumActionResult.PASS;
        }

        ItemStack removedUpgrade = CraftingUnitTransformationRegistry.getInstance().getRemovedUpgrade(this);
        if (removedUpgrade.isEmpty()) {
            return EnumActionResult.PASS;
        }

        T tile = this.getTileEntity(world, pos);
        if (tile != null && tile.getCluster() != null && tile.getCluster().isBusy()) {
            player.sendStatusMessage(PlayerMessages.CraftingCpuBusy.text(), true);
            return EnumActionResult.SUCCESS;
        }

        if (!this.transform(world, pos, newState)) {
            return EnumActionResult.PASS;
        }

        giveOrDrop(player, removedUpgrade);

        return EnumActionResult.SUCCESS;
    }

    private boolean transform(World world, BlockPos pos, IBlockState newState) {
        return !world.isRemote && world.setBlockState(pos, newState, 3);
    }

    @Override
    public ICraftingUnitDefinition getCraftingUnitDefinition(IBlockState state, IBlockAccess world, BlockPos pos) {
        return this.definition;
    }

    @Override
    public boolean isCompatibleCraftingUnit(IBlockState selfState, IBlockAccess world, BlockPos selfPos,
                                            IBlockState otherState, BlockPos otherPos) {
        if (!(world.getTileEntity(selfPos) instanceof ICraftingCPUTileEntity selfTile)) {
            return false;
        }
        if (!(world.getTileEntity(otherPos) instanceof ICraftingCPUTileEntity otherTile)) {
            return false;
        }
        return selfTile.isCompatibleCraftingUnit(otherTile);
    }
}
