package ae2.client.render.cablebus;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

import org.jetbrains.annotations.Nullable;

public class FacadeBlockAccess implements IBlockAccess {

    private final IBlockAccess level;
    private final BlockPos pos;
    private final IBlockState state;

    public FacadeBlockAccess(IBlockAccess level, BlockPos pos, IBlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return this.level.getTileEntity(pos);
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return this.level.getCombinedLight(pos, lightValue);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        if (this.pos.equals(pos)) {
            return this.state;
        }
        return this.level.getBlockState(pos);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        IBlockState state = this.getBlockState(pos);
        return state.getBlock().isAir(state, this, pos);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return this.level.getBiome(pos);
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return this.level.getStrongPower(pos, direction);
    }

    @Override
    public WorldType getWorldType() {
        return this.level.getWorldType();
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        if (pos.getX() < -30000000 || pos.getZ() < -30000000 || pos.getX() >= 30000000 || pos.getZ() >= 30000000) {
            return _default;
        }
        return this.getBlockState(pos).isSideSolid(this, pos, side);
    }
}
