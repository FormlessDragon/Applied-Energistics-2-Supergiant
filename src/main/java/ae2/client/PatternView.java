package ae2.client;

import ae2.client.gui.pattern.PatternGuiHandler;
import ae2.crafting.pattern.EncodedPatternItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.List;

@SideOnly(Side.CLIENT)
public final class PatternView {

    public static final PatternView INSTANCE = new PatternView();

    private PatternView() {
    }

    @SubscribeEvent
    public void addPatternViewTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (ActionKey.VIEW_PATTERN.isUnbound()
            || !(stack.getItem() instanceof EncodedPatternItem<?>)) {
            return;
        }

        String keyName = ActionKey.VIEW_PATTERN.getDisplayName();
        List<String> tooltip = event.getToolTip();
        tooltip.add(Math.min(1, tooltip.size()), TextFormatting.DARK_GRAY
            + I18n.format("pattern.tooltip", TextFormatting.GRAY + keyName + TextFormatting.DARK_GRAY));

        if (ActionKey.VIEW_PATTERN.isActiveAndMatches(Keyboard.getEventKey())) {
            ItemStack pattern = stack.copy();
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.addScheduledTask(() -> {
                if (!PatternGuiHandler.open(pattern) && minecraft.player != null) {
                    minecraft.player.sendMessage(new TextComponentTranslation("chat.pattern_view.error"));
                }
            });
        }
    }
}
