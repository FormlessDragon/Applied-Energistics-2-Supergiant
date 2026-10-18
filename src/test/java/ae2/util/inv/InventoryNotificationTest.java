package ae2.util.inv;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryNotificationTest {
    @Test
    void extractionNotifiesOnceAndHostFailureStillSavesAndReleasesGuard() {
        Bootstrap.register();
        var host = new Host();
        var inventory = new AppEngInternalInventory(host, 1);
        inventory.setItemDirect(0, new ItemStack(Items.APPLE, 2));
        host.changes = host.saves = 0;
        assertEquals(1, inventory.extractItem(0, 1, true).getCount());
        assertEquals(0, host.changes);
        assertEquals(1, inventory.extractItem(0, 1, false).getCount());
        assertEquals(1, inventory.extractItem(0, 1, false).getCount());
        assertTrue(inventory.getStackInSlot(0).isEmpty());
        assertEquals(2, host.changes);
        assertEquals(2, host.saves);

        host.failChange = true;
        host.failSave = true;
        var original = assertThrows(IllegalStateException.class,
            () -> inventory.setItemDirect(0, new ItemStack(Items.CARROT)));
        assertEquals("host update", original.getMessage());
        assertEquals(1, original.getSuppressed().length);
        assertEquals("save", original.getSuppressed()[0].getMessage());
        assertEquals(3, host.saves, "mutation must still issue its save request after the host callback fails");
        host.failChange = host.failSave = false;
        inventory.setItemDirect(0, new ItemStack(Items.APPLE, 3));
        assertEquals(4, host.changes);
        assertEquals(4, host.saves);
        var restored = new AppEngInternalInventory(1);
        restored.readFromNBT(host.saved, "inventory");
        assertEquals(Items.APPLE, restored.getStackInSlot(0).getItem());
        assertEquals(3, restored.getStackInSlot(0).getCount());
    }

    private static final class Host implements InternalInventoryHost {
        int changes, saves;
        boolean failChange, failSave;
        NBTTagCompound saved;

        public boolean isClientSide() {
            return false;
        }

        public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
            changes++;
            if (failChange) throw new IllegalStateException("host update");
        }

        public void saveChangedInventory(AppEngInternalInventory inventory) {
            saves++;
            if (failSave) throw new IllegalStateException("save");
            saved = new NBTTagCompound();
            inventory.writeToNBT(saved, "inventory");
        }
    }
}
