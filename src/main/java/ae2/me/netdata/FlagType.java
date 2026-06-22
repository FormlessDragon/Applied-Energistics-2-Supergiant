package ae2.me.netdata;

public enum FlagType {
    LINK,
    NODE;

    private static final FlagType[] VALUES = values();

    public static FlagType byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
