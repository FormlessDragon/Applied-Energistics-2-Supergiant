package appeng.entity;

import appeng.client.EffectType;
import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.core.definitions.AEItems;
import appeng.recipes.transform.TransformLogic;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

public final class EntityChargedQuartz extends AEBaseEntityItem {
    private int delay;
    private int transformTime;

    @SuppressWarnings("unused")
    public EntityChargedQuartz(World world) {
        super(world);
    }

    public EntityChargedQuartz(World world, double x, double y, double z, ItemStack stack) {
        super(world, x, y, z, stack);
    }

    public static boolean applies(ItemStack stack) {
        return AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.is(stack);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (this.isDead) {
            return;
        }

        if (this.world.isRemote && this.delay > 30 && AEConfig.instance().isEnableEffects()) {
            AppEng.instance().spawnEffect(EffectType.Lightning, this.world, this.posX, this.posY, this.posZ, null);
            this.delay = 0;
        }

        this.delay++;

        int x = MathHelper.floor(this.posX);
        int y = MathHelper.floor((this.getEntityBoundingBox().minY + this.getEntityBoundingBox().maxY) / 2.0D);
        int z = MathHelper.floor(this.posZ);

        IBlockState state = this.world.getBlockState(new BlockPos(x, y, z));
        Material material = state.getMaterial();

        Fluid fluid = material.isLiquid() ? FluidRegistry.lookupFluidForBlock(state.getBlock()) : null;

        if (!this.world.isRemote && fluid != null) {
            this.transformTime++;
            if (this.transformTime > 60 && !TransformLogic.tryTransform(this,
                circumstance -> circumstance.isFluid(fluid))) {
                this.transformTime = 0;
            }
        } else {
            this.transformTime = 0;
        }
    }
}
