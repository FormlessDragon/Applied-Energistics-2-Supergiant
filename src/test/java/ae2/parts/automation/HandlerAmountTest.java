package ae2.parts.automation;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEItemKey;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerAmountTest {
    @Test
    void receiptEqualsActualIntRequestAndSimulationDoesNotMutate() {
        Bootstrap.register();
        var inventory = new ItemStackHandler(1);
        var key = AEItemKey.of(Items.APPLE);
        for (long amount : new long[]{0, -1, 1, Integer.MAX_VALUE, (long) Integer.MAX_VALUE + 10, Long.MAX_VALUE}) {
            long expected = Math.clamp(amount, 0, 64);
            assertEquals(expected, HandlerStrategy.ITEMS.insert(inventory, key, amount, Actionable.SIMULATE));
            assertTrue(inventory.getStackInSlot(0).isEmpty());
        }
        assertEquals(64, HandlerStrategy.ITEMS.insert(inventory, key, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(64, inventory.getStackInSlot(0).getCount());
        assertEquals(0, HandlerStrategy.ITEMS.insert(inventory, key, Long.MAX_VALUE, Actionable.MODULATE));
    }
}
