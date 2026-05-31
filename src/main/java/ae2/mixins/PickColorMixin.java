package ae2.mixins;

import ae2.hooks.ColorApplicatorPickColorHook;
import ae2.hooks.WirelessTerminalPickBlockHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class PickColorMixin {
    @Shadow
    public EntityPlayerSP player;

    @Shadow
    public RayTraceResult objectMouseOver;

    @Inject(method = "middleClickMouse", at = @At("HEAD"), cancellable = true)
    private void pickColor(CallbackInfo ci) {
        if (this.player != null && this.objectMouseOver != null
            && this.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            if (ColorApplicatorPickColorHook.onPickColor(this.player, this.objectMouseOver)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "middleClickMouse", at = @At("TAIL"))
    private void wirelessPickBlock(CallbackInfo ci) {
        if (this.player != null && this.objectMouseOver != null
            && this.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            WirelessTerminalPickBlockHook.onPickBlock(this.player, this.objectMouseOver);
        }
    }
}
