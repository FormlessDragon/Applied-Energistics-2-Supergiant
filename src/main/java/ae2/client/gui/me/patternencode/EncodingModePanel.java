package ae2.client.gui.me.patternencode;

import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Icon;
import ae2.client.gui.WidgetContainer;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;

abstract class EncodingModePanel implements ICompositeWidget {
    protected final AEBaseGui<? extends ContainerPatternEncodingTerm> screen;
    protected final ContainerPatternEncodingTerm container;
    protected final WidgetContainer widgets;
    protected Point position = Point.ZERO;
    protected int width;
    protected int height;
    protected boolean visible;

    EncodingModePanel(AEBaseGui<? extends ContainerPatternEncodingTerm> screen, WidgetContainer widgets) {
        this.screen = screen;
        this.container = screen.getContainer();
        this.widgets = widgets;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void setPosition(Point position) {
        this.position = position;
    }

    @Override
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(this.position.x(), this.position.y(), this.width, this.height);
    }

    abstract Icon getIcon();

    public abstract ITextComponent getTabTooltip();
}
