package ae2.mixins;

import ae2.client.EffectType;
import ae2.core.AEConfig;
import ae2.core.AppEng;
import ae2.recipes.transform.FluidTransformProtectedItem;
import ae2.recipes.transform.TransformCircumstance;
import ae2.recipes.transform.TransformLogic;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityItem.class)
public abstract class ItemEntityTransformMixin extends Entity implements FluidTransformProtectedItem {
    @Unique
    private static final int AE2_FLUID_DAMAGE_PROTECTION_TICKS = 200;

    @Unique
    private int ae2_delay;

    @Unique
    private int ae2_transformTime;

    @Unique
    private int ae2_fluidDamageProtectionTicks;

    @Unique
    private Fluid ae2_craftedFluidProtection;

    @Unique
    private int ae2_craftedFluidProtectionTicks;

    public ItemEntityTransformMixin(World world) {
        super(world);
    }

    @Inject(method = "attackEntityFrom", at = @At("HEAD"), cancellable = true)
    private void handleExplosionTransform(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        EntityItem self = (EntityItem) (Object) this;
        if (self.world.isRemote || self.isDead) {
            return;
        }

        if (source.isExplosion()
            && TransformLogic.canTransformInExplosion(self)
            && TransformLogic.tryTransform(self, TransformCircumstance::isExplosion)) {
            cir.setReturnValue(false);
            return;
        }

        if (!ae2_$isFluidDamage(source)) {
            return;
        }

        boolean transformOutput = this.ae2_craftedFluidProtection != null;
        if (!transformOutput && !TransformLogic.canProtectFromFluidDamage(self)) {
            return;
        }

        Fluid fluid = ae2_$getFluidAtEntity(self);
        if (fluid == null) {
            return;
        }

        if (ae2_$shouldProtectCraftedOutput(fluid)
            || TransformLogic.canProtectFromFluidDamage(self, fluid) && ae2_$shouldProtectFromFluidDamage(self, fluid)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void handleFluidTransform(CallbackInfo ci) {
        EntityItem self = (EntityItem) (Object) this;
        if (self.isDead) {
            return;
        }

        ae2_$tickCraftedOutputProtection(self);

        if (!TransformLogic.canTransformInAnyFluid(self)) {
            return;
        }

        Fluid fluid = ae2_getTransformFluid(self);
        if (fluid == null) {
            this.ae2_transformTime = 0;
            this.ae2_fluidDamageProtectionTicks = 0;
            return;
        }

        if (self.world.isRemote) {
            if (this.ae2_delay++ > 30 && AEConfig.instance().isEnableEffects()) {
                AppEng.instance().spawnEffect(EffectType.Lightning, self.world, self.posX, self.posY, self.posZ, null);
                this.ae2_delay = 0;
            }
            return;
        }

        self.extinguish();
        this.ae2_fluidDamageProtectionTicks++;
        this.ae2_transformTime++;
        if (this.ae2_transformTime > 60
            && !TransformLogic.tryTransform(self, circumstance -> circumstance.isFluid(fluid))) {
            this.ae2_transformTime = 0;
        }
    }

    @Unique
    private Fluid ae2_getTransformFluid(EntityItem self) {
        Fluid fluid = ae2_$getFluidAtEntity(self);
        if (!TransformLogic.canTransformInFluid(self, fluid)) {
            return null;
        }
        return fluid;
    }

    @Override
    public void ae2_protectFromTransformFluid(Fluid fluid) {
        this.ae2_craftedFluidProtection = fluid;
        this.ae2_craftedFluidProtectionTicks = 0;
    }

    @Unique
    private Fluid ae2_$getFluidAtEntity(EntityItem self) {
        int x = MathHelper.floor(self.posX);
        int y = MathHelper.floor((self.getEntityBoundingBox().minY + self.getEntityBoundingBox().maxY) / 2.0D);
        int z = MathHelper.floor(self.posZ);
        IBlockState state = self.world.getBlockState(new BlockPos(x, y, z));
        Material material = state.getMaterial();
        return material.isLiquid() ? FluidRegistry.lookupFluidForBlock(state.getBlock()) : null;
    }

    @Unique
    private boolean ae2_$shouldProtectFromFluidDamage(EntityItem self, Fluid fluid) {
        return this.ae2_fluidDamageProtectionTicks <= AE2_FLUID_DAMAGE_PROTECTION_TICKS
            || TransformLogic.hasIngredients(self, circumstance -> circumstance.isFluid(fluid));
    }

    @Unique
    private boolean ae2_$shouldProtectCraftedOutput(Fluid fluid) {
        if (this.ae2_craftedFluidProtection == null || !this.ae2_craftedFluidProtection.getName().equals(fluid.getName())) {
            return false;
        }

        this.extinguish();
        return true;
    }

    @Unique
    private void ae2_$tickCraftedOutputProtection(EntityItem self) {
        if (this.ae2_craftedFluidProtection == null) {
            return;
        }

        Fluid fluid = ae2_$getFluidAtEntity(self);
        if (fluid != null
            && this.ae2_craftedFluidProtection.getName().equals(fluid.getName())
            && this.ae2_craftedFluidProtectionTicks++ <= AE2_FLUID_DAMAGE_PROTECTION_TICKS) {
            return;
        }

        this.ae2_craftedFluidProtection = null;
        this.ae2_craftedFluidProtectionTicks = 0;
    }

    @Unique
    private static boolean ae2_$isFluidDamage(DamageSource source) {
        return source == DamageSource.IN_FIRE
            || source == DamageSource.ON_FIRE
            || source == DamageSource.LAVA;
    }
}
