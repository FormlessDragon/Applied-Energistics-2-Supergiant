package ae2.client.gui.color;

import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.style.WidgetStyle;
import ae2.client.gui.widgets.IconButton;
import ae2.core.AppEng;
import ae2.util.ColorData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.ResourceLocation;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ColorWindow extends ClickableArea {
    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/color_configer.png");
    private static final Blitter BACKGROUND = Blitter.texture(TEXTURE, 200, 200).src(0, 0, 110, 95);

    private final List<Element> elements = new ArrayList<>();
    private final DraggableArea red;
    private final DraggableArea green;
    private final DraggableArea blue;
    private final DraggableArea alpha;
    private final ColorArea preview;
    private final List<GuiButton> buttons = new ObjectArrayList<>(2);
    private Enum<?> configType;
    private boolean visible;

    public ColorWindow(int x, int y, int width, int height, AEBaseGui<?> parent, Runnable apply, Runnable cancel) {
        super(x, y, width, height, parent, () -> {
        });

        addElement("color_red", this.red = new DraggableArea(0, 0, 0, 0, parent));
        addElement("color_green", this.green = new DraggableArea(0, 0, 0, 0, parent));
        addElement("color_blue", this.blue = new DraggableArea(0, 0, 0, 0, parent));
        addElement("color_alpha", this.alpha = new DraggableArea(0, 0, 0, 0, parent));
        addElement("color_preview", this.preview = new ColorArea(0, 0, 0, 0, parent, () -> {
        }));
        this.buttons.add(new ColorActionButton(Icon.ENTER,
            new TextComponentTranslation("gui.ae2.Set"), apply));
        this.buttons.add(new ColorActionButton(Icon.CLEAR,
            new TextComponentTranslation("gui.ae2.Cancel"), cancel));
    }

    private void addElement(String id, ClickableArea area) {
        this.elements.add(new Element(id, area));
    }

    public Enum<?> getConfigType() {
        return this.configType;
    }

    public ColorData getColor() {
        return this.preview.getColor();
    }

    public void open(Enum<?> configType, ColorData color) {
        this.configType = configType;
        setColor(color);
        this.visible = true;
    }

    public void close() {
        this.visible = false;
        this.buttons.forEach(button -> {
            button.visible = false;
            button.enabled = false;
        });
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public void drawForegroundLayer(Rectangle bounds, Point mouse) {
    }

    @Override
    public void drawAbsoluteLayer(Rectangle bounds, Point mouse) {
        if (!this.visible) {
            return;
        }

        updatePreview();
        BACKGROUND.copy().dest(bounds.x + this.x, bounds.y + this.y).blit();
        for (Element element : this.elements) {
            if (element.area instanceof DrawableArea drawable) {
                drawable.draw();
            }
        }
    }

    @Override
    public void populateScreen(Consumer<GuiButton> addWidget, Rectangle bounds, AEBaseGui<?> screen) {
        if (screen.getStyle() == null) {
            return;
        }

        Rectangle relativeBounds = new Rectangle(0, 0, bounds.width, bounds.height);
        for (Element element : this.elements) {
            if (element.id == null) {
                continue;
            }

            WidgetStyle widgetStyle = screen.getStyle().getWidget(element.id);
            element.area.setPosition(widgetStyle.resolve(relativeBounds));
            element.area.setSize(widgetStyle.getWidth(), widgetStyle.getHeight());
        }

        positionButton("color_apply", this.buttons.get(0), relativeBounds, screen);
        positionButton("color_cancel", this.buttons.get(1), relativeBounds, screen);
        this.buttons.forEach(button -> {
            button.visible = this.visible;
            button.enabled = this.visible;
        });
        this.buttons.forEach(addWidget);
    }

    @Override
    public boolean clickRelative(double x, double y) {
        if (!this.visible || !isMouseOverRelative(x, y)) {
            return false;
        }

        for (Element element : this.elements) {
            if (element.area.clickRelative(x, y)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        if (!this.visible) {
            return false;
        }
        if (isMouseOverButton(mousePos)) {
            return false;
        }
        clickRelative(mousePos.x(), mousePos.y());
        return true;
    }

    @Override
    public boolean wantsAllMouseDownEvents() {
        return true;
    }

    @Override
    public void releaseRelative(double x, double y) {
        for (Element element : this.elements) {
            element.area.releaseRelative(x, y);
        }
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        if (isMouseOverButton(mousePos)) {
            return false;
        }
        releaseRelative(mousePos.x(), mousePos.y());
        return this.visible;
    }

    @Override
    public boolean wantsAllMouseUpEvents() {
        return true;
    }

    @Override
    public boolean onMouseDrag(Point mousePos, int button) {
        for (Element element : this.elements) {
            if (element.area instanceof DraggableArea draggable) {
                draggable.dragRelative(mousePos.x(), mousePos.y());
            }
        }
        return this.visible;
    }

    private void setColor(ColorData color) {
        this.red.setValue(color.red());
        this.green.setValue(color.green());
        this.blue.setValue(color.blue());
        this.alpha.setValue(color.alpha());
        this.buttons.forEach(button -> {
            button.visible = true;
            button.enabled = true;
        });
        updatePreview();
    }

    private void updatePreview() {
        this.preview.setColor(new ColorData(this.alpha.getValue(), this.red.getValue(),
            this.green.getValue(), this.blue.getValue()));
    }

    private void positionButton(String id, GuiButton button, Rectangle bounds, AEBaseGui<?> screen) {
        WidgetStyle widgetStyle = screen.getStyle().getWidget(id);
        Point position = widgetStyle.resolve(bounds);
        button.x = screen.getGuiLeft() + position.x();
        button.y = screen.getGuiTop() + position.y();
        button.width = widgetStyle.getWidth();
        button.height = widgetStyle.getHeight();
    }

    private boolean isMouseOverButton(Point mousePos) {
        int absoluteX = this.screen.getGuiLeft() + mousePos.x();
        int absoluteY = this.screen.getGuiTop() + mousePos.y();
        for (GuiButton button : this.buttons) {
            if (button.visible && absoluteX >= button.x && absoluteY >= button.y
                && absoluteX < button.x + button.width && absoluteY < button.y + button.height) {
                return true;
            }
        }
        return false;
    }

    private static final class ColorActionButton extends IconButton {
        private final Icon icon;

        private ColorActionButton(Icon icon, TextComponentTranslation message, Runnable onPress) {
            super(onPress);
            this.icon = icon;
            setMessage(message);
        }

        @Override
        protected Icon getIcon() {
            return this.icon;
        }
    }

    private record Element(String id, ClickableArea area) {
    }
}
