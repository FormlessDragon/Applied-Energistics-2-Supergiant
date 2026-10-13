package ae2.integration.modules.hei;

import ae2.api.client.PatternRecipeTransferEvent;
import ae2.api.client.PatternRecipeTransferSlot;
import ae2.container.me.patternencode.ContainerPatternEncodingTerm;
import ae2.core.AELog;
import ae2.integration.Integrations;
import ae2.parts.encoding.EncodingMode;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SideOnly(Side.CLIENT)
final class PatternRecipeTransferEvents {

    private PatternRecipeTransferEvents() {
    }

    static boolean post(ContainerPatternEncodingTerm container, EncodingMode mode, String recipeCategoryUid,
                        @Nullable ResourceLocation craftingRecipeId,
                        List<PatternRecipeTransferSlot> inputs, List<PatternRecipeTransferSlot> outputs) {
        if (!container.isClientSide()) {
            return false;
        }

        String recipeCategoryTitle = Integrations.hei().getRecipeCategoryTitle(recipeCategoryUid);
        var event = new PatternRecipeTransferEvent(container, mode, recipeCategoryUid, recipeCategoryTitle,
            craftingRecipeId, inputs, outputs);
        try {
            return MinecraftForge.EVENT_BUS.post(event);
        } catch (RuntimeException e) {
            AELog.error(e, "A listener of PatternRecipeTransferEvent failed for recipe type " + recipeCategoryUid);
            return event.isCanceled();
        }
    }
}
