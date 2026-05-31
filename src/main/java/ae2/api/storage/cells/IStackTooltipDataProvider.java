package ae2.api.storage.cells;

import ae2.items.storage.StorageCellTooltipComponent;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Optional;

public interface IStackTooltipDataProvider {
    default void addToTooltip(ItemStack stack, List<String> lines) {
    }

    Optional<StorageCellTooltipComponent> getStackTooltipData(ItemStack stack);

    default Optional<StorageCellTooltipComponent> getTooltipImage(ItemStack stack) {
        return getStackTooltipData(stack);
    }
}
