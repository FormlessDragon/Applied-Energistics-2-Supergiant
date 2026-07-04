package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.DynamicIconButton;
import ae2.container.implementations.ContainerCaner;
import ae2.core.localization.GuiText;
import ae2.tile.misc.CanerMode;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

public class GuiCaner extends AEBaseGui<ContainerCaner> {
    private final DynamicIconButton modeButton;

    public GuiCaner(ContainerCaner container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
        super(container, playerInventory, style);
        this.modeButton = addToLeftToolbar(new DynamicIconButton(
            () -> container.getMode() == CanerMode.FILL ? Icon.ARROW_RIGHT : Icon.ARROW_LEFT,
            GuiText.canerMode(container.getMode()).text(),
            () -> List.of(GuiText.canerMode(container.getMode()).text()),
            container::switchMode));
    }
}
