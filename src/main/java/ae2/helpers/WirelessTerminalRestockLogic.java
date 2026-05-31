package ae2.helpers;

import ae2.api.stacks.AEItemKey;
import net.minecraft.item.ItemStack;

final class WirelessTerminalRestockLogic {
    private WirelessTerminalRestockLogic() {
    }

    static int getRestockAmount(ItemStack stack, AEItemKey key) {
        int maxStackSize = Math.min(stack.getMaxStackSize(), key.getMaxStackSize());
        return getRestockAmount(stack.getCount(), maxStackSize);
    }

    static int getRestockAmount(int currentCount, int maxStackSize) {
        if (maxStackSize <= 1) {
            return 0;
        }

        int threshold = (maxStackSize + 1) / 2;
        if (currentCount >= threshold || currentCount >= maxStackSize) {
            return 0;
        }

        return maxStackSize - currentCount;
    }
}
