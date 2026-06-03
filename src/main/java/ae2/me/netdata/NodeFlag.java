package ae2.me.netdata;

public enum NodeFlag {
    NORMAL,
    DENSE,
    MISSING;

    public static NodeFlag byIndex(int index) {
        NodeFlag[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
