package ae2.me.netdata;

public enum LinkFlag {
    NORMAL,
    DENSE,
    COMPRESSED;

    public static LinkFlag byIndex(int index) {
        LinkFlag[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
