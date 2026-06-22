package ae2.me.netdata;

public enum NodeFlag {
    NORMAL,
    DENSE,
    MISSING;

    private static final NodeFlag[] VALUES = values();

    public static NodeFlag byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
