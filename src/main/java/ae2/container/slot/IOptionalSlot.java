package ae2.container.slot;

import ae2.client.Point;

public interface IOptionalSlot {
    default boolean isRenderDisabled() {
        return false;
    }

    boolean isSlotEnabled();

    default Point getBackgroundPos() {
        return Point.ZERO;
    }
}
