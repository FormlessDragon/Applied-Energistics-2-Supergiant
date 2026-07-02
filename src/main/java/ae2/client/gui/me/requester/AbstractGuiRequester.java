package ae2.client.gui.me.requester;

import ae2.api.behaviors.ContainerItemStrategies;
import ae2.api.behaviors.EmptyingAction;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.me.common.StackSizeRenderer;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.client.gui.widgets.Scrollbar;
import ae2.container.me.common.AbstractContainerRequester;
import ae2.container.slot.RequestSlot;
import ae2.core.AppEng;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.core.network.serverbound.RequesterSlotUpdatePacket;
import ae2.helpers.InventoryAction;
import ae2.tile.crafting.requester.Request;
import ae2.tile.crafting.requester.RequestList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

public abstract class AbstractGuiRequester<C extends AbstractContainerRequester> extends AEBaseGui<C>
    implements RequesterDisplay {

    protected static final int GUI_WIDTH = 195;
    protected static final int GUI_HEADER_HEIGHT = 20;
    protected static final int GUI_FOOTER_HEIGHT = 99;
    protected static final int ROW_HEIGHT = 19;
    protected static final int MIN_ROW_COUNT = 3;
    private static final int GUI_PADDING_X = 8;
    private static final int GUI_PADDING_Y = 6;
    private static final int TEXT_MARGIN_X = 2;
    private static final int TEXT_MAX_WIDTH = 156;
    private static final int SLOT_X = GUI_PADDING_X + ROW_HEIGHT;
    private static final int SLOT_Y_OFFSET = 2;

    private static final Rectangle HEADER_BBOX = new Rectangle(0, 0, GUI_WIDTH, GUI_HEADER_HEIGHT);
    private static final Rectangle TEXT_BBOX = new Rectangle(0, 60, GUI_WIDTH, ROW_HEIGHT);
    private static final Rectangle REQUEST_BBOX = new Rectangle(0, 38, GUI_WIDTH, ROW_HEIGHT);
    protected final InventoryPlayer playerInventory;
    protected final GuiStyle style;
    protected final Scrollbar scrollbar;
    protected final ArrayList<Object> lines = new ArrayList<>();
    private final ResourceLocation texture;
    private final ObjectArrayList<RequestRowWidget> requestWidgets = new ObjectArrayList<>();

    protected boolean refreshList;
    protected int rowAmount = MIN_ROW_COUNT;

    protected AbstractGuiRequester(C container, InventoryPlayer playerInventory, @Nullable ITextComponent title,
                                   GuiStyle style, ResourceLocation texture) {
        super(container, playerInventory, style);
        this.playerInventory = playerInventory;
        this.style = style;
        this.texture = texture;
        this.scrollbar = this.widgets.addScrollBar("scrollbar", Scrollbar.BIG);
        this.xSize = GUI_WIDTH;
        if (title != null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, title);
        }
    }

    private static void readRequest(ClientRequester requester, int index, @Nullable NBTTagCompound tag) {
        Request request = requester.getRequests().get(index);
        if (tag != null) {
            request.readFromNBT(tag);
        }
        request.setRequesterLocation(requester.getRequesterId(), index);
    }

    protected static ResourceLocation requesterTexture(String name) {
        return AppEng.makeId("textures/guis/" + name + ".png");
    }

    @Override
    public final void postClearAll() {
        clearData();
        refreshList();
    }

    @Override
    public final void postFullUpdate(long requesterId, @Nullable ITextComponent requesterName, long sortValue,
                                     int requestCount, Int2ObjectMap<NBTTagCompound> rows) {
        ClientRequester requester = getById(requesterId, requesterName, sortValue, requestCount);
        RequestList requests = requester.getRequests();
        for (int i = 0; i < requests.size(); i++) {
            readRequest(requester, i, rows.get(i));
        }
        afterFullUpdate(requesterId, requesterName, sortValue, requestCount);
        refreshList();
    }

    @Override
    public final void postIncrementalUpdate(long requesterId, Int2ObjectMap<NBTTagCompound> rows) {
        ClientRequester requester = findById(requesterId);
        if (requester != null) {
            RequestList requests = requester.getRequests();
            for (var entry : rows.int2ObjectEntrySet()) {
                int index = entry.getIntKey();
                if (index >= 0 && index < requests.size()) {
                    readRequest(requester, index, entry.getValue());
                }
            }
        }
        afterIncrementalUpdate(requesterId);
        refreshList();
    }

    @Override
    public void initGui() {
        clearRequestWidgets();
        this.xSize = GUI_WIDTH;
        this.rowAmount = Math.min(this.rowAmount, this.container.getRequestSlots().size());
        this.ySize = GUI_HEADER_HEIGHT + GUI_FOOTER_HEIGHT + this.rowAmount * ROW_HEIGHT + 1;

        super.initGui();

        this.requestWidgets.ensureCapacity(this.rowAmount);
        for (int i = 0; i < this.rowAmount; i++) {
            int y = (i + 1) * ROW_HEIGHT + 1;
            var widget = new RequestRowWidget(this, this.style, new Request(), y);
            widget.addButtons(this.buttonList);
            this.requestWidgets.add(widget);
        }

        refreshList();
        resetScrollbar();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateVisibleRows();
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        blit(HEADER_BBOX, offsetX, offsetY);

        int scrollLevel = this.scrollbar.getCurrentScroll();
        int currentY = offsetY + GUI_HEADER_HEIGHT;

        blit(getFooterBounds(), offsetX, currentY + this.rowAmount * ROW_HEIGHT);

        for (int i = 0; i < this.rowAmount; i++) {
            boolean isRequestElement = false;
            if (scrollLevel + i < this.lines.size()) {
                Object lineElement = this.lines.get(scrollLevel + i);
                isRequestElement = lineElement instanceof Request;
            }

            blit(isRequestElement ? REQUEST_BBOX : TEXT_BBOX, offsetX, currentY);
            currentY += ROW_HEIGHT;
        }

        updateVisibleRows();
        for (RequestRowWidget row : this.requestWidgets) {
            row.draw(offsetX, offsetY);
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        if (this.lines.isEmpty()) {
            String text = GuiText.RequesterNoRequesters.getLocal();
            int textWidth = this.fontRenderer.getStringWidth(text);
            this.fontRenderer.drawString(text, (GUI_WIDTH - textWidth) / 2 - 10,
                GUI_PADDING_Y + GUI_HEADER_HEIGHT, 0xe0e0e0);
            return;
        }

        int scrollLevel = this.scrollbar.getCurrentScroll();
        for (int i = 0; i < this.rowAmount; i++) {
            if (scrollLevel + i >= this.lines.size()) {
                continue;
            }

            Object lineElement = this.lines.get(scrollLevel + i);
            if (lineElement instanceof Request request) {
                requestWidgets.get(i).setRequester(request);
            } else if (lineElement instanceof String text) {
                int rows = getByName(text).size();
                if (rows > 1) {
                    text = String.format("%s (%s)", text, rows);
                }
                text = this.fontRenderer.trimStringToWidth(text, TEXT_MAX_WIDTH, true);
                this.fontRenderer.drawString(text, GUI_PADDING_X + TEXT_MARGIN_X,
                    GUI_PADDING_Y + GUI_HEADER_HEIGHT + i * ROW_HEIGHT, 0xe0e0e0);
            }
        }

        for (RequestRowWidget row : this.requestWidgets) {
            row.drawPreview(offsetX, offsetY);
        }
    }

    @Override
    protected void drawSlot(Slot slot) {
        if (!(slot instanceof RequestSlot appEngSlot)) {
            super.drawSlot(slot);
            return;
        }

        if (appEngSlot.getRawStack().isEmpty()) {
            super.drawSlot(slot);
            return;
        }

        ItemStack stack = appEngSlot.getDisplayStack();
        if (stack.isEmpty()) {
            return;
        }

        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        this.itemRender.renderItemAndEffectIntoGUI(this.mc.player, stack, slot.xPos, slot.yPos);
        this.itemRender.renderItemOverlayIntoGUI(this.fontRenderer, stack, slot.xPos, slot.yPos, "");

        GenericStack genericStack = GenericStack.unwrapItemStack(appEngSlot.getRawStack());
        if (genericStack != null && genericStack.amount() > 1) {
            String amount = genericStack.what().formatAmount(genericStack.amount(), AmountFormat.SLOT);
            StackSizeRenderer.renderSizeLabel(this.fontRenderer, slot.xPos, slot.yPos, amount, false);
        }

        renderHoveredSlotOverlayIfNeeded(slot);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        for (RequestRowWidget row : this.requestWidgets) {
            if (row.drawTooltip(this, mouseX, mouseY)) {
                return;
            }
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (handleSelectionPopupMouseClicked(mouseX, mouseY)) {
            return;
        }

        RequestRowWidget clickedInputRow = null;
        for (RequestRowWidget row : this.requestWidgets) {
            if (row.isMouseOverInput(mouseX, mouseY)) {
                clickedInputRow = row;
                break;
            }
        }

        for (RequestRowWidget row : this.requestWidgets) {
            if (row != clickedInputRow) {
                row.clearFocus();
            }
        }

        if (clickedInputRow != null) {
            clearRegisteredTextFieldFocus();
            if (clickedInputRow.mouseClicked(mouseX, mouseY, mouseButton)) {
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void handleMouseClick(@Nullable Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (!(slot instanceof RequestSlot requestSlot)) {
            super.handleMouseClick(slot, slotId, mouseButton, clickType);
            return;
        }

        if (requestSlot.isLocked()) {
            return;
        }

        if (mouseButton == 1 && getEmptyingAction(slot, this.playerInventory.getItemStack()) != null) {
            var packet = new InventoryActionPacket(
                this.container.windowId,
                InventoryAction.EMPTY_ITEM,
                requestSlot.getRequestIndex(),
                requestSlot.getRequesterId()
            );
            InitNetwork.sendToServer(packet);
            return;
        }

        InventoryAction action = getInventoryAction(mouseButton, clickType);

        if (action != null) {
            InitNetwork.sendToServer(new InventoryActionPacket(this.container.windowId, action,
                requestSlot.getRequestIndex(), requestSlot.getRequesterId()));
        }
    }

    @Nullable
    @Override
    protected EmptyingAction getEmptyingAction(@Nullable Slot slot, ItemStack carried) {
        if (slot instanceof RequestSlot && !carried.isEmpty()) {
            return ContainerItemStrategies.getEmptyingAction(carried);
        }

        return super.getEmptyingAction(slot, carried);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        for (RequestRowWidget row : this.requestWidgets) {
            if (row.keyTyped(typedChar, keyCode)) {
                return;
            }
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        for (RequestRowWidget row : this.requestWidgets) {
            row.confirmFocusedInput();
        }
        super.onGuiClosed();
    }

    protected final void resetScrollbar() {
        this.scrollbar.setHeight(this.rowAmount * ROW_HEIGHT + 1);
        this.scrollbar.setRange(0, this.lines.size() - this.rowAmount, 2);
        updateVisibleRows();
    }

    protected void afterFullUpdate(long requesterId, @Nullable ITextComponent requesterName, long sortValue,
                                   int requestCount) {
    }

    protected void afterIncrementalUpdate(@SuppressWarnings("unused") long requesterId) {
    }

    protected void clearData() {
    }

    protected abstract void refreshList();

    protected abstract Collection<?> getByName(String name);

    protected abstract ClientRequester getById(long requesterId, @Nullable ITextComponent name, long sortValue,
                                                  int requestCount);

    protected abstract @Nullable ClientRequester findById(long requesterId);

    protected abstract Rectangle getFooterBounds();

    private void updateVisibleRows() {
        int scrollLevel = this.scrollbar.getCurrentScroll();
        for (int i = 0; i < this.requestWidgets.size(); i++) {
            RequestRowWidget widget = this.requestWidgets.get(i);
            int lineIndex = scrollLevel + i;
            if (lineIndex < this.lines.size() && this.lines.get(lineIndex) instanceof Request request) {
                widget.setRequester(request);
                widget.setVisible(true);
                updateRequestSlot(i, request, true);
            } else {
                widget.setVisible(false);
                updateRequestSlot(i, null, false);
            }
            widget.setOrigin(this.guiLeft, this.guiTop);
        }
    }

    private void clearRequestWidgets() {
        for (RequestRowWidget row : this.requestWidgets) {
            row.removeButtons(this.buttonList);
        }
        this.requestWidgets.clear();
    }

    private void clearRegisteredTextFieldFocus() {
        for (GuiTextField textField : this.widgets.getTextFields()) {
            textField.setFocused(false);
        }

        if (this instanceof ITextFieldGui textFieldGui) {
            for (GuiTextField textField : textFieldGui.getTextFields()) {
                textField.setFocused(false);
            }
        }
    }

    private void updateRequestSlot(int row, @Nullable Request request, boolean visible) {
        RequestSlot slot = this.container.getRequestSlots().get(row);
        if (!visible || request == null || !request.hasRequesterLocation()) {
            boolean changed = slot.clearRequest();
            slot.setSlotEnabled(false);
            slot.setActive(false);
            if (changed) {
                InitNetwork.sendToServer(RequesterSlotUpdatePacket.hidden(this.container.windowId, row));
            }
            return;
        }

        boolean changed = slot.setRequest(request);
        slot.xPos = SLOT_X;
        slot.yPos = (row + 1) * ROW_HEIGHT + SLOT_Y_OFFSET;
        slot.setSlotEnabled(true);
        slot.setActive(true);
        if (changed) {
            InitNetwork.sendToServer(RequesterSlotUpdatePacket.visible(this.container.windowId, row,
                slot.getRequesterId(), slot.getRequestIndex()));
        }
    }

    @Nullable
    private InventoryAction getInventoryAction(int mouseButton, ClickType clickType) {
        return switch (clickType) {
            case PICKUP ->
                mouseButton == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
            case QUICK_MOVE -> mouseButton == 1 ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
            case CLONE -> this.mc.player.capabilities.isCreativeMode ? InventoryAction.CREATIVE_DUPLICATE : null;
            default -> null;
        };
    }

    private void blit(Rectangle srcRect, int x, int y) {
        Blitter.texture(this.texture).copy()
               .src(srcRect)
               .dest(x, y)
               .blit();
    }
}
