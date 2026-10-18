package ae2.api.stacks;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AEItemKeyMatchesTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void fastRejectsEmptyDifferentItemAndDamageBeforeFullComparison() {
        var key = AEItemKey.of(new ItemStack(Items.IRON_PICKAXE));
        assertFalse(key.matches(ItemStack.EMPTY));
        assertFalse(key.matches(new ItemStack(Items.APPLE)));

        var differentDamage = new ItemStack(Items.IRON_PICKAXE);
        differentDamage.setItemDamage(1);
        assertFalse(key.matches(differentDamage));

        var equal = new ItemStack(Items.IRON_PICKAXE, 16);
        assertTrue(key.matches(equal));
    }
}
