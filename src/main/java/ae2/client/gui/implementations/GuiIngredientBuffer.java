package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.GuiStyle;
import ae2.container.implementations.ContainerIngredientBuffer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiIngredientBuffer extends AEBaseGui<ContainerIngredientBuffer> {
    public GuiIngredientBuffer(ContainerIngredientBuffer container, InventoryPlayer playerInventory,
                               ITextComponent title, GuiStyle style) {
        super(container, playerInventory, style);
    }
}
