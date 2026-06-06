package ae2.client.gui.implementations;

import ae2.api.config.ActionItems;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.me.items.GuiSetProcessingPatternAmount;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.PaletteColor;
import ae2.client.gui.widgets.ActionButton;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerStorageBus;
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

public class GuiPreciseStorageBus extends GuiSpecialStorageBus<ContainerStorageBus> {
    public GuiPreciseStorageBus(ContainerStorageBus container, InventoryPlayer playerInventory, ITextComponent title,
                                GuiStyle style) {
        super(container, playerInventory, title, style);
        addToLeftToolbar(new ActionButton(ActionItems.CLOSE, container::clear));
        addToLeftToolbar(new ActionButton(ActionItems.COG, container::partition));
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
        this.fontRenderer.drawString(GuiText.PreciseBusSetAmount.getLocal(), 0, 13, color);
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
                        AEParts.PRECISE_STORAGE_BUS.stack().getTextComponent()));
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
                itemTooltip.add(Tooltips.getAmountTooltip(ButtonToolTips.Amount, unwrapped).getFormattedText());
            }
            itemTooltip.add(Tooltips.getSetAmountTooltip().getFormattedText());
            drawTooltipLines(mouseX, mouseY, itemTooltip);
            return;
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private boolean canModifyAmount(Slot slot) {
        return slot != null
            && slot.isEnabled()
            && slot.getHasStack()
            && this.container.getSlots(SlotSemantics.CONFIG).contains(slot);
    }
}
