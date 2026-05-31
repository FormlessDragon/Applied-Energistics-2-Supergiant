package ae2.client;

import org.jspecify.annotations.NonNull;

import java.awt.Rectangle;

public record Point(int x, int y) {
    public static final Point ZERO = new Point(0, 0);

    public static Point fromTopLeft(Rectangle bounds) {
        return new Point(bounds.x, bounds.y);
    }

    public Point move(int x, int y) {
        return new Point(this.x + x, this.y + y);
    }

    public boolean isIn(Rectangle rect) {
        if (this.x < rect.x
            || this.y < rect.y) return false;
        if (this.x >= rect.x + rect.width) return false;
        return this.y < rect.y + rect.height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Point(int x1, int y1))) {
            return false;
        }
        return x == x1 && y == y1;
    }

    @Override
    public @NonNull String toString() {
        return "Point{"
            + "x=" + x
            + ", y=" + y
            + '}';
    }
}
