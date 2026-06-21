package ae2.items.materials;

import ae2.entity.EntitySingularity;
import ae2.items.AEBaseItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class MaterialItem extends AEBaseItem {
    public MaterialItem() {
        super();
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return EntitySingularity.applies(stack);
    }

    @Override
    public Entity createEntity(World world, Entity location, ItemStack itemstack) {
        Entity entity;
        if (EntitySingularity.applies(itemstack)) {
            entity = new EntitySingularity(world, location.posX, location.posY, location.posZ, itemstack);
        } else {
            return null;
        }

        entity.motionX = location.motionX;
        entity.motionY = location.motionY;
        entity.motionZ = location.motionZ;

        if (entity instanceof EntityItem entityItem) {
            entityItem.setDefaultPickupDelay();
        }

        return entity;
    }
}
