package ae2.client.gui.implementations;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.ProgressBar;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.GuiIds;
import ae2.container.implementations.ContainerCrystalAssembler;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SwitchGuisPacket;
import ae2.tile.misc.TileCrystalAssembler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class GuiCrystalAssembler extends GuiUpgradeable<ContainerCrystalAssembler> {
    private final ProgressBar progressBar;
    private final SettingToggleButton<YesNo> autoExportBtn;
    private final IconButton outputSidesBtn;
    private String lastProgressText = "";

    public GuiCrystalAssembler(ContainerCrystalAssembler container, InventoryPlayer playerInventory,
                               ITextComponent title, GuiStyle style) {
        super(container, playerInventory, title, style);
        this.progressBar = new ProgressBar(this.container, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        this.widgets.add("progressBar", this.progressBar);
        this.autoExportBtn = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.AUTO_EXPORT, YesNo.NO));
        this.outputSidesBtn = addToLeftToolbar(new IconButton(this::openOutputSides) {
            {
                setMessage(ButtonToolTips.OutputSideConfig.text());
            }

            @Override
            protected Icon getIcon() {
                return Icon.OUTPUT_SIDE_CONFIG;
            }

            @Override
            public List<ITextComponent> getTooltipMessage() {
                return List.of(
                    ButtonToolTips.OutputSideConfig.text(),
                    ButtonToolTips.OutputSideConfigHint.text());
            }
        });
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        int maxProgress = this.container.getMaxProgress();
        int progress = maxProgress > 0 ? this.container.getCurrentProgress() * 100 / maxProgress : 0;
        updateProgressTooltip(progress + "%");
        this.autoExportBtn.set(this.container.getAutoExport());
        this.outputSidesBtn.setVisibility(this.container.getAutoExport() == YesNo.YES);
    }

    private void updateProgressTooltip(String progressText) {
        if (!progressText.equals(this.lastProgressText)) {
            this.lastProgressText = progressText;
            this.progressBar.setFullMsg(new TextComponentString(progressText));
        }
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        Slot slot = getSlotUnderMouse();
        if (this.playerInventory.getItemStack().isEmpty() && slot != null && this.container.isTankSlot(slot)
            && slot.getHasStack()) {
            var tooltip = new ObjectArrayList<>(getItemToolTip(slot.getStack()));
            GenericStack unwrapped = GenericStack.fromItemStack(slot.getStack());
            long amount = unwrapped == null ? 0 : unwrapped.amount();
            tooltip.add(TextFormatting.GRAY + GuiText.CrystalAssemblerAmount.getLocal(amount,
                TileCrystalAssembler.TANK_CAPACITY));
            drawTooltipLines(mouseX, mouseY, tooltip);
            return;
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private void openOutputSides() {
        InitNetwork.sendToServer(SwitchGuisPacket.openSubGui(GuiIds.GuiKey.OUTPUT_SIDES));
    }
}
