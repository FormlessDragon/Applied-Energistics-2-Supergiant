package ae2.container.slot;

import ae2.api.inventories.InternalInventory;

public class ResizableSlot extends AppEngSlot {
    private final String styleId;
    private int width = 16;
    private int height = 16;

    public ResizableSlot(InternalInventory inventory, int slotIndex, int x, int y, String styleId) {
        super(inventory, slotIndex, x, y);
        this.styleId = styleId;
    }

    public String getStyleId() {
        return this.styleId;
    }

    public int getWidth() {
        return this.width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return this.height;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}
