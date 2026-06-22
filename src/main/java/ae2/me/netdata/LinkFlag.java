package ae2.me.netdata;

public enum LinkFlag {
    NORMAL,
    DENSE,
    COMPRESSED;

    private static final LinkFlag[] VALUES = values();

    public static LinkFlag byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
