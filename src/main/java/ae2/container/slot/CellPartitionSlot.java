package ae2.container.slot;

import ae2.api.inventories.InternalInventory;
import ae2.api.storage.StorageCells;
import ae2.client.Point;
import ae2.core.localization.GuiText;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CellPartitionSlot extends FakeSlot implements IOptionalSlot {
    private final IPartitionSlotHost host;
    private final int slot;

    public CellPartitionSlot(InternalInventory inventory, IPartitionSlotHost host, int slotIndex, int x, int y) {
        super(inventory, slotIndex, x, y);
        this.host = host;
        this.slot = slotIndex;
    }

    public CellPartitionSlot(InternalInventory inventory, IPartitionSlotHost host, int slotIndex) {
        this(inventory, host, slotIndex, 0, 0);
    }

    @Override
    public ItemStack getStack() {
        if (!this.isSlotEnabled()) {
            this.clearStack();
        }
        return super.getStack();
    }

    @Override
    public boolean isSlotEnabled() {
        return this.host != null && this.host.isPartitionSlotEnabled(this.slot);
    }

    @Override
    public void putStack(ItemStack stack) {
        if (canFitInsideCell(stack)) {
            super.putStack(stack);
        }
    }

    @Override
    public boolean isRenderDisabled() {
        return true;
    }

    @Override
    public Point getBackgroundPos() {
        return new Point(this.xPos - 1, this.yPos - 1);
    }

    @Override
    @Nullable
    public List<ITextComponent> getCustomTooltip(ItemStack carriedItem) {
        if (!canFitInsideCell(carriedItem)) {
            return List.of(GuiText.CantFitInsideStorageCell.text()
                                                           .setStyle(new Style().setColor(TextFormatting.RED)));
        }
        return super.getCustomTooltip(carriedItem);
    }

    private boolean canFitInsideCell(ItemStack stack) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        return cellInventory == null || cellInventory.canFitInsideCell();
    }
}
