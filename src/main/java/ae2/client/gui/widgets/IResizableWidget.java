package ae2.client.gui.widgets;

import ae2.client.Point;

public interface IResizableWidget {
    default void move(Point pos) {
        move(pos.x(), pos.y());
    }

    void move(int x, int y);

    void resize(int width, int height);
}

