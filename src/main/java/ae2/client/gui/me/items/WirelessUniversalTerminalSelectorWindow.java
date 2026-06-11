package ae2.client.gui.me.items;

import ae2.api.implementations.items.WirelessTerminalDefinition;
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
import ae2.client.gui.widgets.ITooltip;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.ItemStackButton;
import ae2.container.AEBaseContainer;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SelectWirelessTerminalPacket;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.items.tools.powered.WirelessTerminalRegistry;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.util.List;
import java.util.function.Consumer;

public class WirelessUniversalTerminalSelectorWindow implements ICompositeWidget {
    private static final GuiStyle STYLE = GuiStyleManager.loadStyleDoc("/screens/wireless_universal_terminal_selector.json");
    private static final String BACK_WIDGET = "back";
    private static final String FIRST_TERMINAL_WIDGET = "terminal0";
    private static final String SECOND_TERMINAL_WIDGET = "terminal1";
    private static final String LAST_TERMINAL_WIDGET = "terminal5";
    private static final int DEFAULT_WIDTH = 200;
    private static final int DEFAULT_HEIGHT = 82;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 7;

    private final AEBaseGui<? extends AEBaseContainer> parent;
    private final List<GuiButton> buttons = new ObjectArrayList<>();
    private Rectangle bounds = new Rectangle(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private Point screenOrigin = Point.ZERO;
    private boolean visible;
    private boolean dragging;
    private Point dragOffset = Point.ZERO;
    private boolean resetPositionOnOpen = true;

    public WirelessUniversalTerminalSelectorWindow(AEBaseGui<? extends AEBaseContainer> parent) {
        this.parent = parent;
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

    private static int getWindowHeight() {
        GeneratedBackground background = STYLE.getGeneratedBackground();
        return background != null ? background.getHeight() : DEFAULT_HEIGHT;
    }

    private static int getWindowHeight(int terminalCount) {
        TerminalLayout layout = getTerminalLayout();
        int rows = Math.max(1, (terminalCount + layout.columns() - 1) / layout.columns());
        return layout.baseHeight() + Math.max(0, rows - 1) * layout.stepY();
    }

    private static TerminalLayout getTerminalLayout() {
        WidgetStyle first = STYLE.getWidget(FIRST_TERMINAL_WIDGET);
        WidgetStyle second = STYLE.getWidget(SECOND_TERMINAL_WIDGET);
        WidgetStyle last = STYLE.getWidget(LAST_TERMINAL_WIDGET);
        int firstLeft = requireCoordinate(first.getLeft(), FIRST_TERMINAL_WIDGET, "left");
        int firstTop = requireCoordinate(first.getTop(), FIRST_TERMINAL_WIDGET, "top");
        int secondLeft = requireCoordinate(second.getLeft(), SECOND_TERMINAL_WIDGET, "left");
        int lastLeft = requireCoordinate(last.getLeft(), LAST_TERMINAL_WIDGET, "left");
        int stepX = secondLeft - firstLeft;
        int stepY = first.getHeight() + 10;
        int columns = Math.max(1, ((lastLeft - firstLeft) / Math.max(1, stepX)) + 1);
        return new TerminalLayout(
            firstLeft,
            firstTop,
            stepX,
            stepY,
            columns,
            first.getWidth(),
            first.getHeight(),
            getWindowHeight());
    }

    private static int requireCoordinate(@Nullable Integer coordinate, String widgetId, String axis) {
        if (coordinate == null) {
            throw new IllegalStateException("Missing " + axis + " for widget " + widgetId);
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
            GuiText.WirelessTerminalSelector.getLocal(),
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

    private void rebuildButtons() {
        this.buttons.clear();
        if (!this.visible) {
            return;
        }

        IconButton closeButton = new IconButton(this::close) {
            @Override
            protected Icon getIcon() {
                return Icon.CLEAR;
            }
        };
        closeButton.setMessage(GuiText.Close.text());
        moveCloseButton(closeButton);
        this.buttons.add(closeButton);

        WirelessTerminalGuiHost<?> wirelessHost = getWirelessHost();
        if (wirelessHost == null) {
            return;
        }
        ItemStack stack = wirelessHost.getItemStack();
        if (!(stack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal)) {
            return;
        }

        var terminalLayout = getTerminalLayout();
        int index = 0;
        for (WirelessTerminalDefinition definition : WirelessTerminalRegistry.allDefinitions()) {
            if (!universalTerminal.hasTerminal(stack, definition.item())) {
                continue;
            }
            ItemStackButton button = new ItemStackButton(
                definition.icon(),
                definition.displayName(),
                () -> selectTerminal(definition.id()));
            moveTerminalButton(button, terminalLayout, index);
            this.buttons.add(button);
            index++;
        }
    }

    private void selectTerminal(String id) {
        close();
        InitNetwork.sendToServer(new SelectWirelessTerminalPacket(
            this.parent.getContainer().windowId,
            id));
    }

    @Nullable
    private WirelessTerminalGuiHost<?> getWirelessHost() {
        return this.parent.getContainer().getItemGuiHost() instanceof WirelessTerminalGuiHost<?> wirelessHost
            ? wirelessHost : null;
    }

    private void moveCloseButton(GuiButton button) {
        WidgetStyle widgetStyle = STYLE.getWidget(BACK_WIDGET);
        Point pos = widgetStyle.resolve(new Rectangle(0, 0, this.bounds.width, this.bounds.height));
        button.x = this.screenOrigin.x() + this.bounds.x + pos.x();
        button.y = this.screenOrigin.y() + this.bounds.y + pos.y();
        button.width = widgetStyle.getWidth() != 0 ? widgetStyle.getWidth() : button.width;
        button.height = widgetStyle.getHeight() != 0 ? widgetStyle.getHeight() : button.height;
    }

    private void moveTerminalButton(GuiButton button, TerminalLayout layout, int index) {
        int column = index % layout.columns();
        int row = index / layout.columns();
        button.x = this.screenOrigin.x() + this.bounds.x + layout.originX() + column * layout.stepX();
        button.y = this.screenOrigin.y() + this.bounds.y + layout.originY() + row * layout.stepY();
        button.width = layout.buttonWidth();
        button.height = layout.buttonHeight();
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
        updateWindowHeight(getInstalledTerminalCount());
        this.bounds.x = (this.parent.width - this.bounds.width) / 2 - this.parent.getGuiLeft();
        this.bounds.y = (this.parent.height - this.bounds.height) / 2 - this.parent.getGuiTop();
        clampToScreen();
    }

    private void clampToScreen() {
        updateWindowHeight(getInstalledTerminalCount());
        int minX = -this.parent.getGuiLeft();
        int minY = -this.parent.getGuiTop();
        int maxX = this.parent.width - this.parent.getGuiLeft() - this.bounds.width;
        int maxY = this.parent.height - this.parent.getGuiTop() - this.bounds.height;
        this.bounds.x = Math.clamp(this.bounds.x, minX, Math.max(minX, maxX));
        this.bounds.y = Math.clamp(this.bounds.y, minY, Math.max(minY, maxY));
    }

    private void updateWindowHeight(int terminalCount) {
        this.bounds.width = getWindowWidth();
        this.bounds.height = getWindowHeight(terminalCount);
    }

    private int getInstalledTerminalCount() {
        WirelessTerminalGuiHost<?> wirelessHost = getWirelessHost();
        if (wirelessHost == null) {
            return 0;
        }
        ItemStack stack = wirelessHost.getItemStack();
        if (!(stack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal)) {
            return 0;
        }

        int count = 0;
        for (WirelessTerminalDefinition definition : WirelessTerminalRegistry.allDefinitions()) {
            if (universalTerminal.hasTerminal(stack, definition.item())) {
                count++;
            }
        }
        return count;
    }

    private record TerminalLayout(int originX, int originY, int stepX, int stepY, int columns,
                                  int buttonWidth, int buttonHeight, int baseHeight) {
    }
}
