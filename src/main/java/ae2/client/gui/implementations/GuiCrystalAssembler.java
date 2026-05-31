package ae2.client.gui.implementations;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ProgressBar;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerCrystalAssembler;
import ae2.core.localization.Tooltips;
import ae2.tile.misc.TileCrystalAssembler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

public class GuiCrystalAssembler extends GuiUpgradeable<ContainerCrystalAssembler> {
    private final ProgressBar progressBar;
    private final SettingToggleButton<YesNo> autoExportBtn;

    public GuiCrystalAssembler(ContainerCrystalAssembler container, InventoryPlayer playerInventory,
                               ITextComponent title, GuiStyle style) {
        super(container, playerInventory, title, style);
        this.progressBar = new ProgressBar(this.container, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        this.widgets.add("progressBar", this.progressBar);
        this.autoExportBtn = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.AUTO_EXPORT, YesNo.NO));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        int maxProgress = this.container.getMaxProgress();
        int progress = maxProgress > 0 ? this.container.getCurrentProgress() * 100 / maxProgress : 0;
        this.progressBar.setFullMsg(new TextComponentString(progress + "%"));
        this.autoExportBtn.set(this.container.getAutoExport());
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        Slot slot = getSlotUnderMouse();
        if (this.playerInventory.getItemStack().isEmpty() && slot != null && this.container.isTankSlot(slot)
            && slot.getHasStack()) {
            var tooltip = new ObjectArrayList<>(getItemToolTip(slot.getStack()));
            GenericStack unwrapped = GenericStack.fromItemStack(slot.getStack());
            long amount = unwrapped == null ? 0 : unwrapped.amount();
            tooltip.add(Tooltips.of(new TextComponentTranslation("gui.ae2.CrystalAssemblerAmount", amount,
                TileCrystalAssembler.TANK_CAPACITY)).getFormattedText());
            drawTooltipLines(mouseX, mouseY, tooltip);
            return;
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }
}
