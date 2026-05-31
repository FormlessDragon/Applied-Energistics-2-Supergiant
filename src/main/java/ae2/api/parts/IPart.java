/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.parts;

import ae2.api.networking.IGridNode;
import ae2.api.util.AECableType;
import ae2.util.Platform;
import ae2.util.SettingsFrom;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public interface IPart extends ICustomCableConnection {

    IPartItem<?> getPartItem();

    @SideOnly(Side.CLIENT)
    default void renderDynamic(double x, double y, double z, float partialTicks, int destroyStage) {
    }

    default boolean requireDynamicRender() {
        return false;
    }

    default boolean isSolid() {
        return false;
    }

    default boolean canConnectRedstone() {
        return false;
    }

    default void writeToNBT(NBTTagCompound data) {
    }

    default void readFromNBT(NBTTagCompound data) {
    }

    default void exportSettings(SettingsFrom mode, NBTTagCompound output) {
    }

    default void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
    }

    default int getLightLevel() {
        return 0;
    }

    default boolean isLadder(EntityLivingBase entity) {
        return false;
    }

    default void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
    }

    default void onUpdateShape(EnumFacing side) {
    }

    default int isProvidingStrongPower() {
        return 0;
    }

    default int isProvidingWeakPower() {
        return 0;
    }

    default void writeToStream(PacketBuffer data) {
    }

    @ApiStatus.Experimental
    default void writeVisualStateToNBT(NBTTagCompound data) {
    }

    default boolean readFromStream(PacketBuffer data) {
        return false;
    }

    @ApiStatus.Experimental
    default void readVisualStateFromNBT(NBTTagCompound data) {
    }

    @Nullable
    IGridNode getGridNode();

    default void onEntityCollision(Entity entity) {
    }

    default void removeFromWorld() {
    }

    default void addToWorld() {
    }

    @Nullable
    default IGridNode getExternalFacingNode() {
        return null;
    }

    default AECableType getExternalCableConnectionType() {
        return AECableType.GLASS;
    }

    void setPartHostInfo(@Nullable EnumFacing side, IPartHost host, TileEntity blockEntity);

    default boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        return false;
    }

    default boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        return false;
    }

    default boolean onClicked(EntityPlayer player, Vec3d pos) {
        return false;
    }

    default boolean onShiftClicked(EntityPlayer player, Vec3d pos) {
        return false;
    }

    default void addPartDrop(List<ItemStack> drops, boolean ignoredWrenched) {
        var stack = getPartItem().asItemStack();
        var tag = new NBTTagCompound();
        exportSettings(SettingsFrom.DISMANTLE_ITEM, tag);
        if (!Platform.isNbtEmpty(tag)) {
            stack.setTagCompound(tag);
        }
        drops.add(stack);
    }

    @MustBeInvokedByOverriders
    default void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
    }

    @MustBeInvokedByOverriders
    default void clearContent() {
    }

    @Override
    float getCableConnectionLength(AECableType cable);

    default void animateTick(World level, BlockPos pos, Random random) {
    }

    default void onPlacement(EntityPlayer player) {
    }

    default boolean canBePlacedOn(BusSupport what) {
        return what == BusSupport.CABLE;
    }

    default IPartModel getStaticModels() {
        return IPartModel.EMPTY;
    }

    default @org.jspecify.annotations.Nullable Object getModelData() {
        return null;
    }

    void getBoxes(IPartCollisionHelper bch);

    default void addEntityCrashInfo(CrashReportCategory section) {
    }

    default AECableType getDesiredConnectionType() {
        return AECableType.GLASS;
    }
}
