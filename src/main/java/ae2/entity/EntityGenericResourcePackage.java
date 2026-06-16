package ae2.entity;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class EntityGenericResourcePackage extends AEBaseEntityItem {
    public EntityGenericResourcePackage(World world) {
        super(world);
        this.lifespan = Integer.MAX_VALUE;
    }

    public EntityGenericResourcePackage(World world, double x, double y, double z, ItemStack stack) {
        super(world, x, y, z, stack);
        this.lifespan = Integer.MAX_VALUE;
    }
}
