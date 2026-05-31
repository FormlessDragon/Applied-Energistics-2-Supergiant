package ae2.entity;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public abstract class AEBaseEntityItem extends EntityItem {
    public AEBaseEntityItem(World world) {
        super(world);
    }

    public AEBaseEntityItem(World world, double x, double y, double z, ItemStack stack) {
        super(world, x, y, z, stack);
    }
}
