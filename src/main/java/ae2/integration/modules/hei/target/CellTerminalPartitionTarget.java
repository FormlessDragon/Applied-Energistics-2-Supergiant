package ae2.integration.modules.hei.target;

import ae2.api.stacks.GenericStack;
import ae2.client.gui.implementations.GuiCellTerminal;
import mezz.jei.api.gui.IGhostIngredientHandler.Target;
import org.jetbrains.annotations.NotNull;

import java.awt.Rectangle;

public record CellTerminalPartitionTarget<T>(GuiCellTerminal gui,
                                             int slotIndex,
                                             Rectangle area,
                                             GenericStack stack) implements Target<T> {
    @Override
    public Rectangle getArea() {
        return this.area;
    }

    @Override
    public void accept(@NotNull T ingredient) {
        this.gui.acceptPartitionGhost(this.slotIndex, this.stack);
    }
}
