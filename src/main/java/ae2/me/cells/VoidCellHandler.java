package ae2.me.cells;

import ae2.api.stacks.GenericStack;
import ae2.api.storage.cells.ICellHandler;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.storage.cells.ISaveProvider;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.items.storage.StorageCellTooltipComponent;
import ae2.items.storage.VoidCellItem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class VoidCellHandler implements ICellHandler {
    public static final VoidCellHandler INSTANCE = new VoidCellHandler();

    @Override
    public boolean isCell(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof VoidCellItem;
    }

    @Nullable
    @Override
    public VoidCellInventory getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (isCell(stack)) {
            return new VoidCellInventory(stack, host);
        }
        return null;
    }

    public Optional<StorageCellTooltipComponent> getTooltipData(ItemStack stack) {
        if (!(stack.getItem() instanceof ICellWorkbenchItem workbenchItem)) {
            return Optional.empty();
        }

        var upgrades = new ObjectArrayList<ItemStack>();
        if (AEConfig.instance().isTooltipShowCellUpgrades()) {
            for (var upgrade : workbenchItem.getUpgrades(stack)) {
                if (!upgrade.isEmpty()) {
                    upgrades.add(upgrade.copy());
                }
            }
        }

        List<GenericStack> content;
        boolean hasMoreContent;
        if (AEConfig.instance().isTooltipShowCellContent()) {
            content = new ObjectArrayList<>();
            int maxCountShown = AEConfig.instance().getTooltipMaxCellContentShown();
            var inventory = getCellInventory(stack, null);
            if (inventory != null) {
                for (var entry : inventory.getAvailableStacks()) {
                    content.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                }
                content.sort(Comparator.comparingLong(GenericStack::amount).reversed()
                    .thenComparing(entry -> entry.what().getDisplayName().getFormattedText()));
            }

            hasMoreContent = content.size() > maxCountShown;
            if (content.size() > maxCountShown) {
                content = new ObjectArrayList<>(content.subList(0, maxCountShown));
            }
        } else {
            content = Collections.emptyList();
            hasMoreContent = false;
        }

        return Optional.of(new StorageCellTooltipComponent(upgrades, content, hasMoreContent, true));
    }

    public void addPartitionInformation(ItemStack stack, List<String> lines) {
        if (!(stack.getItem() instanceof ICellWorkbenchItem workbenchItem)) {
            return;
        }

        var config = workbenchItem.getConfigInventory(stack);
        if (config.isEmpty()) {
            lines.add(GuiText.Partitioned.getLocal() + " - " + GuiText.Nothing.getLocal());
            return;
        }

        var upgrades = workbenchItem.getUpgrades(stack);
        var includeMode = upgrades.isInstalled(AEItems.INVERTER_CARD.item()) ? GuiText.Excluded : GuiText.Included;
        var precisionMode = upgrades.isInstalled(AEItems.FUZZY_CARD.item()) ? GuiText.Fuzzy : GuiText.Precise;

        lines.add(GuiText.Partitioned.getLocal() +
            " - " +
            includeMode.getLocal() +
            " " +
            precisionMode.getLocal());
    }
}
