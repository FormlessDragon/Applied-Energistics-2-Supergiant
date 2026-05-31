package ae2.items.tools.powered;

public enum WirelessTerminalMagnetMode {
    OFF(false, false),
    PICKUP_INVENTORY(true, false),
    PICKUP_ME(true, true),
    PICKUP_ME_NO_MAGNET(false, true);

    private final boolean magnet;
    private final boolean pickupToME;

    WirelessTerminalMagnetMode(boolean magnet, boolean pickupToME) {
        this.magnet = magnet;
        this.pickupToME = pickupToME;
    }

    public static WirelessTerminalMagnetMode from(boolean magnet, boolean pickupToME) {
        if (magnet && pickupToME) {
            return PICKUP_ME;
        }
        if (magnet) {
            return PICKUP_INVENTORY;
        }
        if (pickupToME) {
            return PICKUP_ME_NO_MAGNET;
        }
        return OFF;
    }

    public static WirelessTerminalMagnetMode fromId(byte id) {
        return values()[id];
    }

    public byte id() {
        return (byte) this.ordinal();
    }

    public boolean magnet() {
        return this.magnet;
    }

    public boolean pickupToME() {
        return this.pickupToME;
    }

    public WirelessTerminalMagnetMode next() {
        return switch (this) {
            case OFF -> PICKUP_INVENTORY;
            case PICKUP_INVENTORY -> PICKUP_ME;
            case PICKUP_ME -> PICKUP_ME_NO_MAGNET;
            case PICKUP_ME_NO_MAGNET -> OFF;
        };
    }
}
