package ae2.api.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface InvalidPatternTooltipStrategy {
    PatternDetailsTooltip getTooltip(ItemStack stack, World level, @Nullable Exception cause, boolean flags);
}
