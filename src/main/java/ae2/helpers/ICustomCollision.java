package ae2.helpers;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public interface ICustomCollision {
    Iterable<AxisAlignedBB> getSelectedBoundingBoxesFromPool(World world, BlockPos pos, Entity entity,
                                                             boolean hitFluids);

    void addCollidingBlockToList(World world, BlockPos pos, AxisAlignedBB bb, List<AxisAlignedBB> out,
                                 Entity entity);
}
