package ae2.mixins;

import ae2.recipes.transform.TransformCircumstance;
import ae2.recipes.transform.TransformLogic;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityItem.class)
public abstract class ItemEntityTransformMixin {
    @Inject(method = "attackEntityFrom", at = @At("HEAD"), cancellable = true)
    private void handleExplosionTransform(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        EntityItem self = (EntityItem) (Object) this;
        if (!source.isExplosion() || self.world.isRemote || self.isDead) {
            return;
        }

        if (TransformLogic.canTransformInExplosion(self)
            && TransformLogic.tryTransform(self, TransformCircumstance::isExplosion)) {
            cir.setReturnValue(false);
        }
    }
}
