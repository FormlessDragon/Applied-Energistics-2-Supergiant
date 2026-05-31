package ae2.items.storage;

import ae2.api.stacks.GenericStack;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;

public record StorageCellTooltipComponent(List<ItemStack> upgrades, List<GenericStack> content, boolean hasMoreContent,
                                          boolean showAmounts) {
    public StorageCellTooltipComponent(List<ItemStack> upgrades, List<GenericStack> content, boolean hasMoreContent,
                                       boolean showAmounts) {
        this.upgrades = Collections.unmodifiableList(upgrades);
        this.content = Collections.unmodifiableList(content);
        this.hasMoreContent = hasMoreContent;
        this.showAmounts = showAmounts;
    }

    public int getRowCount() {
        int rows = 0;
        if (!upgrades.isEmpty()) {
            rows++;
        }
        if (!content.isEmpty()) {
            rows++;
        }
        return rows;
    }
}
