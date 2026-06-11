package ae2.client.gui.implementations;

import ae2.api.config.RedstoneMode;
import ae2.api.config.SchedulingMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.me.items.GuiSetProcessingPatternAmount;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.PaletteColor;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerStockExportBus;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.AEParts;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.helpers.InventoryAction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;

import java.io.IOException;

public class GuiStockExportBus<T extends ContainerStockExportBus> extends GuiUpgradeable<T> {
    private final T stockContainer;
    private final SettingToggleButton<RedstoneMode> redstoneMode;
    private final SettingToggleButton<YesNo> craftMode;
    private final SettingToggleButton<SchedulingMode> schedulingMode;

    public GuiStockExportBus(T container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
        super(container, playerInventory, title, style);
        this.stockContainer = container;
        this.redstoneMode = addToLeftToolbar(
            new ServerSettingToggleButton<>(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE));
        if (container.getHost().getConfigManager().hasSetting(Settings.CRAFT_ONLY)) {
            this.craftMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.CRAFT_ONLY, YesNo.NO));
        } else {
            this.craftMode = null;
        }
        if (container.getHost().getConfigManager().hasSetting(Settings.SCHEDULING_MODE)) {
            this.schedulingMode = addToLeftToolbar(
                new ServerSettingToggleButton<>(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT));
        } else {
            this.schedulingMode = null;
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.redstoneMode.set(container.getRedStoneMode());
        this.redstoneMode.setVisibility(container.hasUpgrade(AEItems.REDSTONE_CARD.item()));
        if (this.craftMode != null) {
            this.craftMode.set(container.getCraftingMode());
            this.craftMode.setVisibility(container.hasUpgrade(AEItems.CRAFTING_CARD.item()));
        }
        if (this.schedulingMode != null) {
            this.schedulingMode.set(container.getSchedulingMode());
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        GlStateManager.pushMatrix();
        GlStateManager.translate(10, 17, 0);
        GlStateManager.scale(0.6f, 0.6f, 1);
        int color = this.style != null
            ? this.style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB() & 0xFFFFFF
            : 0x404040;
        this.fontRenderer.drawString(GuiText.SetAmountButtonHint.getLocal(), 0, 0, color);
        GlStateManager.popMatrix();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 2) {
            Slot slot = findSlot(mouseX, mouseY);
            if (canModifyAmount(slot)) {
                GenericStack currentStack = GenericStack.fromItemStack(slot.getStack());
                if (currentStack != null) {
                    switchToScreen(new GuiSetProcessingPatternAmount(this, currentStack,
                        newStack -> InitNetwork.sendToServer(new InventoryActionPacket(
                            this.container.windowId,
                            InventoryAction.SET_FILTER,
                            slot.slotNumber,
                            GenericStack.wrapInItemStack(newStack))),
                        AEParts.STOCK_EXPORT_BUS.stack().getTextComponent()));
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (mouseButton == 2 && canModifyAmount(slot)) {
            return;
        }
        super.handleMouseClick(slot, slotId, mouseButton, clickType);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        Slot slot = getSlotUnderMouse();
        if (this.playerInventory.getItemStack().isEmpty() && canModifyAmount(slot)) {
            var itemTooltip = new ObjectArrayList<>(getItemToolTip(slot.getStack()));
            GenericStack unwrapped = GenericStack.fromItemStack(slot.getStack());
            if (unwrapped != null) {
                itemTooltip.add(Tooltips.getAmountTooltipLocal(ButtonToolTips.Amount, unwrapped));
            }
            itemTooltip.add(Tooltips.getSetAmountTooltipLocal());
            drawTooltipLines(mouseX, mouseY, itemTooltip);
            return;
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    protected boolean canModifyAmount(Slot slot) {
        return slot != null
            && slot.isEnabled()
            && slot.getHasStack()
            && this.stockContainer.getSlots(SlotSemantics.CONFIG).contains(slot);
    }
}
