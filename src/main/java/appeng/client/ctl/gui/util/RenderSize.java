package appeng.client.ctl.gui.util;

public record RenderSize(int width, int height) {

    public boolean isLimited() {
        return width != -1 && height != -1;
    }

    public boolean isWidthLimited() {
        return width != -1;
    }

    public boolean isHeightLimited() {
        return height != -1;
    }

    public RenderSize smaller(final RenderSize another) {
        return new RenderSize(Math.min(width, another.width), Math.min(height, another.height));
    }

    public RenderSize add(final RenderSize another) {
        return new RenderSize(width + another.width, height + another.height);
    }

    public RenderSize subtract(final RenderSize another) {
        return new RenderSize(width - another.width, height - another.height);
    }

}