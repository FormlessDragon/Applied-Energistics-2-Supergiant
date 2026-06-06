package ae2.client.gui.implementations;

import ae2.api.config.CondenserOutput;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.IconButton;
import ae2.container.implementations.ContainerVoidCell;
import ae2.core.localization.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

public class GuiVoidCell extends AEBaseGui<ContainerVoidCell> {

    private final ModeButton trash;
    private final ModeButton matterBalls;
    private final ModeButton singularity;

    public GuiVoidCell(ContainerVoidCell container, InventoryPlayer playerInventory, ITextComponent title,
                       GuiStyle style) {
        super(container, playerInventory, style);

        this.trash = new ModeButton(CondenserOutput.TRASH, Icon.CONDENSER_OUTPUT_TRASH);
        this.matterBalls = new ModeButton(CondenserOutput.MATTER_BALLS, Icon.CONDENSER_OUTPUT_MATTER_BALL);
        this.singularity = new ModeButton(CondenserOutput.SINGULARITY, Icon.CONDENSER_OUTPUT_SINGULARITY);

        this.widgets.add("trash", this.trash);
        this.widgets.add("matterBalls", this.matterBalls);
        this.widgets.add("singularity", this.singularity);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        CondenserOutput output = this.container.getOutput();
        this.trash.setFocused(output == CondenserOutput.TRASH);
        this.matterBalls.setFocused(output == CondenserOutput.MATTER_BALLS);
        this.singularity.setFocused(output == CondenserOutput.SINGULARITY);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        String mode = GuiText.voidCellMode(this.container.getOutput()).getLocal();
        this.fontRenderer.drawString(mode, 5, 42, 0x404040);
    }

    private final class ModeButton extends IconButton {
        private final CondenserOutput output;
        private final Icon icon;

        private ModeButton(CondenserOutput output, Icon icon) {
            super(() -> GuiVoidCell.this.container.setModeFromClient(output));
            this.output = output;
            this.icon = icon;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            this.setFocused(GuiVoidCell.this.container.getOutput() == this.output);
            super.drawButton(minecraft, mouseX, mouseY, partialTicks);
        }

        @Override
        protected Icon getIcon() {
            return this.icon;
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return List.of(GuiText.voidCellMode(this.output).text());
        }
    }
}
