package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.IconButton;
import ae2.container.implementations.ContainerCaner;
import ae2.tile.misc.CanerMode;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

public class GuiCaner extends AEBaseGui<ContainerCaner> {
    private final ModeButton modeButton;

    public GuiCaner(ContainerCaner container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
        super(container, playerInventory, style);
        this.modeButton = addToLeftToolbar(new ModeButton(container));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.modeButton.setMode(this.container.getMode());
    }

    private static final class ModeButton extends IconButton {
        private CanerMode mode = CanerMode.FILL;

        private ModeButton(ContainerCaner container) {
            super(container::switchMode);
            setMode(container.getMode());
        }

        private void setMode(CanerMode mode) {
            this.mode = mode;
            setMessage(new TextComponentTranslation(mode == CanerMode.FILL
                ? "gui.ae2.caner.fill"
                : "gui.ae2.caner.empty"));
        }

        @Override
        protected Icon getIcon() {
            return this.mode == CanerMode.FILL ? Icon.ARROW_RIGHT : Icon.ARROW_LEFT;
        }
    }
}
