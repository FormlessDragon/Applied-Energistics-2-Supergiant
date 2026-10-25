package ae2.api.stacks;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CheckedKeyCounterTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void inlineMappedAndFuzzyGroupsKeepCheckedAdditionWhenCopied(boolean damageable) {
        var stack = new ItemStack(damageable ? Items.WOODEN_PICKAXE : Items.PAPER);
        var key = AEItemKey.of(stack);
        var counter = KeyCounter.checked();
        counter.add(key, Long.MAX_VALUE);
        var overflow = assertThrows(AmountOverflowException.class, () -> counter.add(key, 1));
        assertEquals(key, overflow.getKey());
        assertEquals(Long.MAX_VALUE, counter.get(key), "the failing addition is not applied");
        var otherStack = stack.copy();
        var tag = new NBTTagCompound();
        tag.setString("variant", "other");
        otherStack.setTagCompound(tag);
        var other = AEItemKey.of(otherStack);
        counter.add(other, 5);
        var copied = KeyCounter.checked();
        copied.addAll(counter);
        assertThrows(AmountOverflowException.class, () -> copied.add(key, 1));
        assertEquals(5, copied.get(other));
        var extra = new KeyCounter();
        extra.add(key, 1);
        assertThrows(AmountOverflowException.class, () -> copied.addAll(extra));
        assertEquals(Long.MAX_VALUE, copied.get(key));
    }
}
