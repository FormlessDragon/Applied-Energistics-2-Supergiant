package ae2.items.parts;

import ae2.api.parts.IPart;
import ae2.api.parts.IPartItem;
import ae2.core.localization.InGameTooltip;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Function;

public class TooltipPartItem<T extends IPart> extends PartItem<T> {
    private final InGameTooltip[] tooltipLines;

    public TooltipPartItem(Class<T> partClass, Function<IPartItem<T>, T> factory, InGameTooltip... tooltipLines) {
        super(partClass, factory);
        this.tooltipLines = tooltipLines;
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        for (InGameTooltip tooltipLine : this.tooltipLines) {
            lines.add(TextFormatting.GRAY + tooltipLine.getLocal());
        }
    }
}
