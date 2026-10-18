package ae2.helpers;

import ae2.api.stacks.AEItemKey;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WirelessTerminalRestockTest {
    @Test
    void pendingAmountIsRecomputedFromCurrentStackCount() {
        Bootstrap.register();
        var key = AEItemKey.of(Items.APPLE);
        var request = new WirelessTerminalActions.RestockRequest(
            Items.APPLE.getRegistryName().toString(), 0, null);
        var stack = new ItemStack(Items.APPLE, 10);

        assertEquals(54, request.currentAmount(stack, key));

        stack.setCount(60);
        assertEquals(0, request.currentAmount(stack, key));

        stack.setCount(64);
        assertEquals(0, request.currentAmount(stack, key));
    }
}
