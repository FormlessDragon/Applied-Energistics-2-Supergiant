package ae2.client.gui.me.common;

import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKeyTypes;
import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Icon;
import ae2.client.gui.Tooltip;
import ae2.client.gui.style.BackgroundGenerator;
import ae2.client.gui.style.GeneratedBackground;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.style.WidgetStyle;
import ae2.client.gui.widgets.AECheckbox;
import ae2.client.gui.widgets.ITooltip;
import ae2.client.gui.widgets.IconButton;
import ae2.container.AEBaseContainer;
import ae2.container.interfaces.IKeyTypeSelectionContainer;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class KeyTypeSelectionWindow<C extends AEBaseContainer & IKeyTypeSelectionContainer>
    implements ICompositeWidget {
    private static final GuiStyle STYLE = GuiStyleManager.loadStyleDoc("/screens/key_type_selection.json");
    private static final String BACK_WIDGET = "back";
    private static final String KEY_TYPES_WIDGET = "keytypes";
    private static final int DEFAULT_WIDTH = 200;
    private static final int DEFAULT_HEIGHT = 66;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 7;
    private static final int PADDING = 6;
    private static final int KEY_TYPE_SPACING = AECheckbox.SIZE + PADDING;

    private final AEBaseGui<C> parent;
    private final ITextComponent title;
    private final ObjectArrayList<GuiButton> buttons = new ObjectArrayList<>();
    private final Object2ObjectLinkedOpenHashMap<AEKeyType, AECheckbox> checkboxes =
        new Object2ObjectLinkedOpenHashMap<>();
    private Rectangle bounds = new Rectangle(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private Point screenOrigin = Point.ZERO;
    private boolean visible;
    private boolean dragging;
    private Point dragOffset = Point.ZERO;
    private boolean resetPositionOnOpen = true;

    public KeyTypeSelectionWindow(AEBaseGui<C> parent, ITextComponent title) {
        this.parent = parent;
        this.title = title;
    }

    private static boolean contains(Rectangle area, int mouseX, int mouseY) {
        return mouseX >= area.x
            && mouseY >= area.y
            && mouseX < area.x + area.width
            && mouseY < area.y + area.height;
    }

    private static int getWindowWidth() {
        GeneratedBackground background = STYLE.getGeneratedBackground();
        return background != null ? background.getWidth() : DEFAULT_WIDTH;
    }

    private static int getWindowHeight(int keyTypeCount) {
        WidgetStyle keyTypesStyle = STYLE.getWidget(KEY_TYPES_WIDGET);
        int top = requireCoordinate(keyTypesStyle.getTop(), "top");
        return top + keyTypeCount * KEY_TYPE_SPACING + PADDING;
    }

    private static int requireCoordinate(@Nullable Integer coordinate, String axis) {
        if (coordinate == null) {
            throw new IllegalStateException("Missing " + axis + " for widget " + KEY_TYPES_WIDGET);
        }
        return coordinate;
    }

    public void open() {
        this.visible = true;
        this.dragging = false;
        this.resetPositionOnOpen = true;
        applyPendingOpenPositionReset();
        rebuildButtons();
    }

    public void close() {
        this.visible = false;
        this.dragging = false;
        this.buttons.clear();
        this.checkboxes.clear();
    }

    public void toggle() {
        if (this.visible) {
            close();
        } else {
            open();
        }
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public void setPosition(Point position) {
        this.bounds = new Rectangle(position.x(), position.y(), this.bounds.width, this.bounds.height);
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(this.bounds);
    }

    @Override
    public void populateScreen(Consumer<GuiButton> addWidget, Rectangle screenBounds, AEBaseGui<?> screen) {
        this.screenOrigin = Point.fromTopLeft(screenBounds);
        if (!applyPendingOpenPositionReset()) {
            clampToScreen();
        }
        rebuildButtons();
    }

    @Override
    public void updateBeforeRender() {
        if (!this.visible) {
            return;
        }

        applyPendingOpenPositionReset();
        syncCheckboxes();

        if (!this.dragging) {
            return;
        }

        int mouseX = Mouse.getX() * this.parent.width / this.parent.mc.displayWidth;
        int mouseY = this.parent.height - Mouse.getY() * this.parent.height / this.parent.mc.displayHeight - 1;
        int relativeMouseX = mouseX - this.parent.getGuiLeft();
        int relativeMouseY = mouseY - this.parent.getGuiTop();

        this.bounds.setLocation(
            relativeMouseX - this.dragOffset.x(),
            relativeMouseY - this.dragOffset.y());
        clampToScreen();
        rebuildButtons();
    }

    @Override
    public void drawAbsoluteLayer(Rectangle screenBounds, Point mouse) {
        if (!this.visible) {
            return;
        }

        int x = screenBounds.x + this.bounds.x;
        int y = screenBounds.y + this.bounds.y;

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 350);
        BackgroundGenerator.draw(this.bounds.width, this.bounds.height, x, y);
        this.parent.getMinecraft().fontRenderer.drawString(
            this.title.getFormattedText(),
            x + TITLE_X,
            y + TITLE_Y,
            0x404040);
        for (GuiButton button : this.buttons) {
            button.drawButton(Minecraft.getMinecraft(), mouse.x(), mouse.y(), 0);
        }
        GlStateManager.popMatrix();
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        if (!this.visible || !contains(this.bounds, mousePos.x(), mousePos.y())) {
            return false;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        for (GuiButton widget : this.buttons) {
            if (widget.visible && widget.mousePressed(minecraft, absoluteMouse.x(), absoluteMouse.y())) {
                widget.playPressSound(minecraft.getSoundHandler());
                return true;
            }
        }
        if (button == 0 && canStartDrag(mousePos)) {
            this.dragging = true;
            this.dragOffset = new Point(mousePos.x() - this.bounds.x, mousePos.y() - this.bounds.y);
        }
        return true;
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        if (!this.visible) {
            return false;
        }

        this.dragging = false;
        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        for (GuiButton widget : this.buttons) {
            widget.mouseReleased(absoluteMouse.x(), absoluteMouse.y());
        }
        return contains(this.bounds, mousePos.x(), mousePos.y());
    }

    @Override
    public boolean wantsAllMouseUpEvents() {
        return this.visible && this.dragging;
    }

    @Override
    public boolean onMouseDrag(Point mousePos, int button) {
        if (!this.visible || !this.dragging || button != 0) {
            return false;
        }

        this.bounds.setLocation(
            mousePos.x() - this.dragOffset.x(),
            mousePos.y() - this.dragOffset.y());
        clampToScreen();
        rebuildButtons();
        return true;
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        if (!this.visible || !contains(this.bounds, mouseX, mouseY)) {
            return null;
        }

        Point absoluteMouse = this.screenOrigin.move(mouseX, mouseY);
        for (GuiButton button : this.buttons) {
            if (button instanceof ITooltip tooltip
                && tooltip.isTooltipAreaVisible()
                && contains(tooltip.getTooltipArea(), absoluteMouse.x(), absoluteMouse.y())) {
                return new Tooltip(tooltip.getTooltipMessage());
            }
        }
        return null;
    }

    @Override
    public boolean blocksTooltips(int mouseX, int mouseY) {
        return this.visible && contains(this.bounds, mouseX, mouseY);
    }

    @Override
    public boolean blocksMouseInteraction(int mouseX, int mouseY) {
        return this.visible && contains(this.bounds, mouseX, mouseY);
    }

    private void rebuildButtons() {
        this.buttons.clear();
        this.checkboxes.clear();
        if (!this.visible) {
            return;
        }

        this.buttons.ensureCapacity(this.parent.getContainer().getClientKeyTypeSelection().keyTypes().size() + 1);

        IconButton closeButton = new IconButton(this::close) {
            @Override
            protected Icon getIcon() {
                return Icon.CLEAR;
            }
        };
        closeButton.setMessage(GuiText.Close.text());
        moveCloseButton(closeButton);
        this.buttons.add(closeButton);

        WidgetStyle keyTypesStyle = STYLE.getWidget(KEY_TYPES_WIDGET);
        int x = requireCoordinate(keyTypesStyle.getLeft(), "left");
        int y = requireCoordinate(keyTypesStyle.getTop(), "top");
        int width = keyTypesStyle.getWidth();

        for (AEKeyType keyType : this.parent.getContainer().getClientKeyTypeSelection().keyTypes().keySet()) {
            ITextComponent text = keyType.getDescription();
            int checkboxWidth = width != 0 ? width
                : 24 + Minecraft.getMinecraft().fontRenderer.getStringWidth(text.getFormattedText());
            AECheckbox checkbox = new AECheckbox(
                this.screenOrigin.x() + this.bounds.x + x,
                this.screenOrigin.y() + this.bounds.y + y,
                checkboxWidth,
                AECheckbox.SIZE,
                Objects.requireNonNull(this.parent.getStyle(), "GUI style has not been initialized"),
                text);
            checkbox.setChangeListener(() -> this.parent.getContainer().selectKeyType(
                this.parent.getContainer().windowId,
                keyType,
                checkbox.isSelected()));
            this.buttons.add(checkbox);
            this.checkboxes.put(keyType, checkbox);
            y += KEY_TYPE_SPACING;
        }
        syncCheckboxes();
    }

    private void syncCheckboxes() {
        int selectedEntryCount = 0;
        AECheckbox selectedEntry = null;

        for (Map.Entry<AEKeyType, AECheckbox> entry : this.checkboxes.entrySet()) {
            boolean selected = this.parent.getContainer().getClientKeyTypeSelection().keyTypes().getBoolean(entry.getKey());
            entry.getValue().setSelected(selected);
            entry.getValue().enabled = true;

            if (selected) {
                selectedEntryCount++;
                selectedEntry = entry.getValue();
            }
        }

        if (selectedEntryCount == 1 && selectedEntry != null) {
            selectedEntry.enabled = false;
        }
    }

    private void moveCloseButton(GuiButton button) {
        WidgetStyle widgetStyle = STYLE.getWidget(BACK_WIDGET);
        Point pos = widgetStyle.resolve(new Rectangle(0, 0, this.bounds.width, this.bounds.height));
        button.x = this.screenOrigin.x() + this.bounds.x + pos.x();
        button.y = this.screenOrigin.y() + this.bounds.y + pos.y();
        button.width = widgetStyle.getWidth() != 0 ? widgetStyle.getWidth() : button.width;
        button.height = widgetStyle.getHeight() != 0 ? widgetStyle.getHeight() : button.height;
    }

    private boolean canStartDrag(Point mousePos) {
        if (!contains(this.bounds, mousePos.x(), mousePos.y())) {
            return false;
        }

        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        for (GuiButton button : this.buttons) {
            if (button.visible && absoluteMouse.x() >= button.x && absoluteMouse.x() < button.x + button.width
                && absoluteMouse.y() >= button.y && absoluteMouse.y() < button.y + button.height) {
                return false;
            }
        }

        return true;
    }

    private boolean applyPendingOpenPositionReset() {
        if (!this.resetPositionOnOpen) {
            return false;
        }

        centerInScreen();
        this.resetPositionOnOpen = false;
        return true;
    }

    private void centerInScreen() {
        updateWindowSize();
        this.bounds.x = (this.parent.width - this.bounds.width) / 2 - this.parent.getGuiLeft();
        this.bounds.y = (this.parent.height - this.bounds.height) / 2 - this.parent.getGuiTop();
        clampToScreen();
    }

    private void clampToScreen() {
        updateWindowSize();
        int minX = -this.parent.getGuiLeft();
        int minY = -this.parent.getGuiTop();
        int maxX = this.parent.width - this.parent.getGuiLeft() - this.bounds.width;
        int maxY = this.parent.height - this.parent.getGuiTop() - this.bounds.height;
        this.bounds.x = Math.clamp(this.bounds.x, minX, Math.max(minX, maxX));
        this.bounds.y = Math.clamp(this.bounds.y, minY, Math.max(minY, maxY));
    }

    private void updateWindowSize() {
        this.bounds.width = getWindowWidth();
        int keyTypeCount = Math.max(
            AEKeyTypes.getAll().size(),
            this.parent.getContainer().getClientKeyTypeSelection().keyTypes().size());
        this.bounds.height = Math.max(DEFAULT_HEIGHT, getWindowHeight(keyTypeCount));
    }
}
