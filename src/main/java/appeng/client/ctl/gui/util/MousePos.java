package appeng.client.ctl.gui.util;

public record MousePos(int mouseX, int mouseY) {

    public MousePos relativeTo(RenderPos renderPos) {
        return new MousePos(mouseX - renderPos.posX(), mouseY - renderPos.posY());
    }

    public MousePos add(RenderPos renderPos) {
        return new MousePos(mouseX + renderPos.posX(), mouseY + renderPos.posY());
    }

}