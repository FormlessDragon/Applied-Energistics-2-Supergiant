package ae2.tile.crafting;

import ae2.api.implementations.IPowerChannelState;
import ae2.api.util.ICustomName;
import ae2.block.crafting.ICraftingUnitType;
import ae2.me.cluster.IAEMultiBlock;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.me.helpers.IGridConnectedTile;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public interface ICraftingCPUTileEntity
    extends IAEMultiBlock<CraftingCPUCluster>, IPowerChannelState, IGridConnectedTile, ICustomName {

    ICraftingUnitType getCraftingUnitType();

    default long getStorageBytes() {
        return getCraftingUnitType().getStorageBytes();
    }

    default int getAcceleratorThreads() {
        return getCraftingUnitType().getAcceleratorThreads();
    }

    void updateStatus(@Nullable CraftingCPUCluster cluster);

    void updateMultiBlock(BlockPos changedPos);

    void updateSubType(boolean updateFormed);

    ClientState getRenderState();

    boolean isFormed();

    boolean isActive();

    boolean isCoreBlock();

    void setCoreBlock(boolean coreBlock);

    @Nullable
    NBTTagCompound getPreviousState();

    void setPreviousState(@Nullable NBTTagCompound previousState);

    void breakCluster();

    void cancelJobAndDropContents();

    void setName(@Nullable String name);

    @Override
    default void setCustomNameFromRenamer(@Nullable String customName) {
        setName(customName);
        onCustomNameChanged();
    }

    World getWorldObj();

    BlockPos getLocation();

    TileEntity getTileEntity();

    record ClientState(boolean formed, boolean powered, EnumSet<EnumFacing> connections) {
        public static final ClientState DEFAULT = new ClientState(false, false, EnumSet.noneOf(EnumFacing.class));

        public ClientState {
            connections = connections.clone();
        }
    }
}
