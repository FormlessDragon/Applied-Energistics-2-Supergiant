package appeng.client.ctl.gui.util;

public record RenderPos(int posX, int posY) {

    public RenderPos add(RenderPos another) {
        return new RenderPos(posX + another.posX, posY + another.posY);
    }

    public RenderPos subtract(RenderPos another) {
        return new RenderPos(posX - another.posX, posY - another.posY);
    }

}