package ae2.client.gui.color;

import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.Blitter;
import ae2.core.AppEng;
import net.minecraft.util.ResourceLocation;

public class DraggableArea extends DrawableArea {
    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/color_configer.png");
    public static final Blitter SLIDER = Blitter.texture(TEXTURE, 200, 200).src(110, 0, 4, 7);

    private double offset;
    private boolean active;
    private float value;

    public DraggableArea(int x, int y, int width, int height, AEBaseGui<?> parent) {
        super(x, y, width, height, parent, () -> {
        });
    }

    public float getValue() {
        return this.value;
    }

    public void setValue(float value) {
        this.value = Math.clamp(value, 0.0F, 1.0F);
    }

    @Override
    public boolean click(double x, double y) {
        if (isMouseOver(x, y)) {
            this.active = true;
            this.offset = x;
            setValue((float) ((x - this.screen.getGuiLeft() - this.x) / this.w));
            return true;
        }
        return false;
    }

    @Override
    public boolean clickRelative(double x, double y) {
        if (isMouseOverRelative(x, y)) {
            this.active = true;
            this.offset = x;
            setValue((float) ((x - this.x) / this.w));
            return true;
        }
        return false;
    }

    @Override
    public void release(double x, double y) {
        this.active = false;
    }

    @Override
    public void releaseRelative(double x, double y) {
        this.active = false;
    }

    public void drag(double x, double y) {
        if (this.active) {
            double move = (x - this.offset) / this.w;
            this.offset = x;
            this.value = Math.clamp((float) (this.value + move), 0.0F, 1.0F);
        }
    }

    public void dragRelative(double x, double y) {
        drag(x, y);
    }

    @Override
    public boolean onMouseDrag(Point mousePos, int button) {
        dragRelative(mousePos.x(), mousePos.y());
        return this.active;
    }

    @Override
    public void draw() {
        int x = this.x + this.screen.getGuiLeft();
        int y = this.y + this.screen.getGuiTop();
        SLIDER.copy().dest((int) (x + this.value * this.w), y).blit();
    }

    @Override
    public void drawRelative() {
        SLIDER.copy().dest((int) (this.x + this.value * this.w), this.y).blit();
    }

    @Override
    public boolean isMouseOverRelative(double x, double y) {
        return x >= this.x && x < this.x + this.w
            && y >= this.y && y < this.y + this.h;
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return x >= this.x + this.screen.getGuiLeft()
            && x < this.x + this.screen.getGuiLeft() + this.w
            && y >= this.y + this.screen.getGuiTop()
            && y < this.y + this.screen.getGuiTop() + this.h;
    }
}
