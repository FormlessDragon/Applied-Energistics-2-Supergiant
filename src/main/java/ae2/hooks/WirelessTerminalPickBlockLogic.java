package ae2.hooks;

final class WirelessTerminalPickBlockLogic {
    private static final int HOTBAR_SIZE = 9;

    private WirelessTerminalPickBlockLogic() {
    }

    static SlotDecision findTargetSlot(int matchingInventorySlot, int currentHotbarSlot, boolean mainHandEmpty,
                                       int firstEmptyHotbarSlot) {
        if (isHotbarSlot(matchingInventorySlot)) {
            return new SlotDecision(matchingInventorySlot, true, false);
        }

        if (matchingInventorySlot >= 0) {
            return new SlotDecision(currentHotbarSlot, false, false);
        }

        int hotbarSlot = currentHotbarSlot;
        if (!mainHandEmpty && isHotbarSlot(firstEmptyHotbarSlot)) {
            hotbarSlot = firstEmptyHotbarSlot;
        }

        return new SlotDecision(hotbarSlot, false, true);
    }

    private static boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot < HOTBAR_SIZE;
    }

    record SlotDecision(int hotbarSlot, boolean shouldSelectHotbarSlot, boolean shouldRequestFromNetwork) {
    }
}
