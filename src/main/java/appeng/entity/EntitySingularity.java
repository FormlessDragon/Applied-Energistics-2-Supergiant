package appeng.entity;

import appeng.core.definitions.AEItems;
import appeng.recipes.transform.TransformCircumstance;
import appeng.recipes.transform.TransformLogic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public final class EntitySingularity extends AEBaseEntityItem {
    @SuppressWarnings("unused")
    public EntitySingularity(World world) {
        super(world);
    }

    public EntitySingularity(World world, double x, double y, double z, ItemStack stack) {
        super(world, x, y, z, stack);
    }

    public static boolean applies(ItemStack stack) {
        return AEItems.SINGULARITY.is(stack)
            || AEItems.ENDER_DUST.is(stack)
            || AEItems.QUANTUM_ENTANGLED_SINGULARITY.is(stack);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (source.isExplosion()) {
            if (!this.world.isRemote) {
                TransformLogic.tryTransform(this, TransformCircumstance::isExplosion);
            }
            return false;
        }

        return super.attackEntityFrom(source, amount);
    }
}
