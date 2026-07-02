package ae2.client.gui.widgets;

import ae2.client.gui.Icon;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class GridSelectionPopup<T> {
    private static final int CELL_SIZE = 18;
    private static final int PADDING = 4;
    private static final int BUTTON_GAP = 2;
    private static final int BACKGROUND_COLOR = 0xAA000000;
    private static final int HOVER_COLOR = 0xFF00FF00;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final List<Entry<T>> entries;
    private final ISelectionHandler<T> selectionHandler;
    private final int x;
    private final int y;
    private int hoveredIndex = -1;

    public GridSelectionPopup(int x, int y, List<Entry<T>> entries, ISelectionHandler<T> selectionHandler) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }
        this.x = x;
        this.y = y;
        this.entries = List.copyOf(entries);
        this.selectionHandler = Objects.requireNonNull(selectionHandler, "selectionHandler");
    }

    public static <T> GridSelectionPopup<T> forButton(GuiButton button, int guiLeft, int guiTop, int guiWidth,
                                                      int guiHeight, List<Entry<T>> entries,
                                                      ISelectionHandler<T> selectionHandler) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }

        int popupWidth = getWidth(entries.size());
        int popupHeight = getHeight(entries.size());
        int buttonX = button.x - guiLeft;
        int buttonY = button.y - guiTop;
        int buttonCenterX = buttonX + button.width / 2;

        int popupX = buttonCenterX < guiWidth / 2
            ? buttonX + button.width + BUTTON_GAP
            : buttonX - popupWidth - BUTTON_GAP;
        int popupY = Math.clamp(buttonY, 0, Math.max(0, guiHeight - popupHeight));

        return new GridSelectionPopup<>(popupX, popupY, entries, selectionHandler);
    }

    private static int getWidth(int entryCount) {
        return getColumns(entryCount) * CELL_SIZE + PADDING * 2;
    }

    private static int getHeight(int entryCount) {
        int columns = getColumns(entryCount);
        return (entryCount + columns - 1) / columns * CELL_SIZE + PADDING * 2;
    }

    private static int getColumns(int entryCount) {
        return (int) Math.ceil(Math.sqrt(entryCount));
    }

    public static <T> List<Entry<T>> entries() {
        return new ObjectArrayList<>();
    }

    public void render(Minecraft minecraft, int mouseX, int mouseY) {
        this.hoveredIndex = hoveredIndex(mouseX, mouseY);
        Gui.drawRect(this.x, this.y, this.x + width(), this.y + height(), BACKGROUND_COLOR);

        for (int i = 0; i < this.entries.size(); i++) {
            drawCell(minecraft, i);
        }
    }

    public boolean mousePressed(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }

        int index = hoveredIndex(mouseX, mouseY);
        if (index >= 0) {
            this.selectionHandler.select(this.entries.get(index).Value());
        }
        return true;
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= this.x && mouseX < this.x + width() && mouseY >= this.y && mouseY < this.y + height();
    }

    public List<ITextComponent> getTooltip(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return Collections.emptyList();
        }
        int index = hoveredIndex(mouseX, mouseY);
        if (index < 0) {
            return Collections.emptyList();
        }
        return this.entries.get(index).tooltipLines();
    }

    private void drawCell(Minecraft minecraft, int index) {
        int iconX = iconX(index);
        int iconY = iconY(index);
        if (this.hoveredIndex == index) {
            Gui.drawRect(iconX, iconY, iconX + CELL_SIZE, iconY + CELL_SIZE, HOVER_COLOR);
        }

        Entry<T> entry = this.entries.get(index);
        if (!entry.itemStack().isEmpty()) {
            renderItem(minecraft, entry.itemStack(), iconX + 1, iconY + 1);
            return;
        }

        String text = entry.text();
        if (text != null) {
            drawText(minecraft, text, iconX, iconY);
            return;
        }

        Icon icon = entry.icon();
        if (icon != null) {
            icon.getBlitter().dest(iconX + 1, iconY + 1).blit();
        }
    }

    private void drawText(Minecraft minecraft, String text, int x, int y) {
        int textWidth = minecraft.fontRenderer.getStringWidth(text);
        int textX = x + (CELL_SIZE - textWidth) / 2;
        int textY = y + (CELL_SIZE - minecraft.fontRenderer.FONT_HEIGHT) / 2;
        minecraft.fontRenderer.drawStringWithShadow(text, textX, textY, TEXT_COLOR);
    }

    private void renderItem(Minecraft minecraft, ItemStack itemStack, int x, int y) {
        RenderItem itemRenderer = minecraft.getRenderItem();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 200.0F);
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        itemRenderer.renderItemAndEffectIntoGUI(itemStack, 0, 0);
        itemRenderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, itemStack, 0, 0, null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    private int hoveredIndex(int mouseX, int mouseY) {
        for (int i = 0; i < this.entries.size(); i++) {
            int iconX = iconX(i);
            int iconY = iconY(i);
            if (mouseX >= iconX && mouseX < iconX + CELL_SIZE && mouseY >= iconY && mouseY < iconY + CELL_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private int iconX(int index) {
        return this.x + PADDING + index % getColumns(this.entries.size()) * CELL_SIZE;
    }

    private int iconY(int index) {
        return this.y + PADDING + index / getColumns(this.entries.size()) * CELL_SIZE;
    }

    private int width() {
        return getWidth(this.entries.size());
    }

    private int height() {
        return getHeight(this.entries.size());
    }

    @FunctionalInterface
    public interface ISelectionHandler<T> {
        void select(T value);
    }

    public record Entry<T>(T Value, @Nullable Icon icon, @Nullable String text, ItemStack itemStack,
                           List<ITextComponent> tooltipLines) {
        public Entry {
            Objects.requireNonNull(Value, "Value");
            Objects.requireNonNull(itemStack, "itemStack");
            tooltipLines = List.copyOf(tooltipLines);
        }

        public static <T> Entry<T> icon(T value, Icon icon, List<ITextComponent> tooltipLines) {
            return new Entry<>(value, icon, null, ItemStack.EMPTY, tooltipLines);
        }

        public static <T> Entry<T> text(T value, String text, List<ITextComponent> tooltipLines) {
            return new Entry<>(value, null, Objects.requireNonNull(text, "text"), ItemStack.EMPTY, tooltipLines);
        }

        public static <T> Entry<T> item(T value, ItemStack itemStack, List<ITextComponent> tooltipLines) {
            return new Entry<>(value, null, null, itemStack, tooltipLines);
        }
    }
}
