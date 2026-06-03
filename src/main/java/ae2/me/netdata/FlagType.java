package ae2.me.netdata;

public enum FlagType {
    LINK,
    NODE;

    public static FlagType byIndex(int index) {
        FlagType[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
