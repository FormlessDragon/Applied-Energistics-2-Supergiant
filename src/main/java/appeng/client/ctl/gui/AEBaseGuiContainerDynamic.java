package appeng.client.ctl.gui;

import appeng.client.ctl.gui.util.MousePos;
import appeng.client.ctl.gui.widget.base.WidgetController;
import appeng.client.gui.AEBaseGui;
import appeng.container.AEBaseContainer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.input.Mouse;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AEBaseGuiContainerDynamic<C extends AEBaseContainer> extends AEBaseGui<C> {

    protected WidgetController widgetController = null;
    protected Slot hoveredSlot = null;

    public AEBaseGuiContainerDynamic(final C container, InventoryPlayer playerInventory) {
        super(container, playerInventory);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        widgetController.update();
    }

    @Override
    public void initGui() {
        super.initGui();
        widgetController.getGui()
                .setGuiLeft(guiLeft)
                .setGuiTop(guiTop)
                .setXSize(width)
                .setYSize(height);
        widgetController.init();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        widgetController.postRender(new MousePos(mouseX, mouseY), false);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY, float partialTicks) {
        widgetController.render(new MousePos(mouseX, mouseY), true);
    }

    @Override
    protected void renderHoveredToolTip(final int mouseX, final int mouseY) {
        updateHoveredSlot(mouseX, mouseY);

        ItemStack stackInSlot = hoveredSlot == null ? ItemStack.EMPTY : hoveredSlot.getStack();
        List<String> hoverTooltips = widgetController.getHoverTooltips(new MousePos(mouseX, mouseY));
        if (stackInSlot.isEmpty() && hoverTooltips.isEmpty()) {
            return;
        }
        List<String> itemTooltip = stackInSlot.isEmpty() ? new ArrayList<>() : this.getItemToolTip(stackInSlot);
        itemTooltip.addAll(hoverTooltips);

        FontRenderer font = stackInSlot.getItem().getFontRenderer(stackInSlot);
        GuiUtils.preItemToolTip(stackInSlot);
        this.drawHoveringText(itemTooltip, mouseX, mouseY, (font == null ? fontRenderer : font));
        GuiUtils.postItemToolTip();
    }

    @Override
    public void handleMouseInput() throws IOException {
        final int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        final int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (widgetController.onMouseInput(new MousePos(mouseX, mouseY))) {
            return;
        }
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) throws IOException {
        if (widgetController.onMouseClicked(new MousePos(mouseX, mouseY), mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(final int mouseX, final int mouseY, final int state) {
        if (widgetController.onMouseReleased(new MousePos(mouseX, mouseY))) {
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(final char typedChar, final int keyCode) throws IOException {
        if (widgetController.onKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawHoveringText(@Nonnull final List<String> textLines, final int x, final int y) {
        super.drawHoveringText(textLines, x, y);
    }

    protected void updateHoveredSlot(final int mouseX, final int mouseY) {
        hoveredSlot = this.inventorySlots.inventorySlots.stream()
                .filter(slot -> this.isMouseOverSlot(slot, mouseX, mouseY) && slot.isEnabled())
                .findFirst()
                .orElse(null);
    }

    protected boolean isMouseOverSlot(Slot slotIn, int mouseX, int mouseY) {
        return this.isPointInRegion(slotIn.xPos, slotIn.yPos, 16, 16, mouseX, mouseY);
    }

}