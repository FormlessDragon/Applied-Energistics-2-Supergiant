package ae2.container.me.patternaccess;

import ae2.api.config.ShowPatternProviders;
import ae2.api.crafting.IPatternDetails;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.VersionedInternalInventory;
import ae2.api.networking.IGrid;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.util.inv.AppEngInternalInventory;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternAccessTrackerTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void displayModesAndPinnedProvidersDoNotReadPatternSlots() {
        PatternContainer provider = new PatternContainer() {
            public IGrid getGrid() {
                return null;
            }

            public InternalInventory getTerminalPatternInventory() {
                throw new AssertionError("This display mode must not request inventory slots");
            }

            public boolean containsPattern(AEItemKey key) {
                return false;
            }

            public PatternContainerGroup getTerminalGroup() {
                return PatternContainerGroup.nothing();
            }
        };
        assertTrue(PatternAccessSession.isVisibleInPatternAccess(provider, ShowPatternProviders.VISIBLE,
            false, null));
        assertTrue(PatternAccessSession.isVisibleInPatternAccess(provider, ShowPatternProviders.ALL,
            false, null));
        assertFalse(PatternAccessSession.isVisibleInPatternAccess(provider, ShowPatternProviders.HIDDEN,
            false, null));
        assertTrue(PatternAccessSession.isVisibleInPatternAccess(provider, ShowPatternProviders.NOT_FULL,
            true, null));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unchangedSlotsAreNotDecodedAgainAndNbtChangesInvalidateOnlyOneSlot(boolean versioned) {
        var inventory = versioned ? new VersionedInventory() : new AppEngInternalInventory(2);
        inventory.setItemDirect(0, new ItemStack(Items.APPLE));
        inventory.setItemDirect(1, new ItemStack(Items.STICK));
        var group = new PatternContainerGroup(null, new TextComponentString("test"), List.of());
        PatternContainer provider = new PatternContainer() {
            public IGrid getGrid() {
                return null;
            }

            public InternalInventory getTerminalPatternInventory() {
                return inventory;
            }

            public boolean containsPattern(AEItemKey key) {
                return false;
            }

            public PatternContainerGroup getTerminalGroup() {
                return group;
            }
        };
        var directory = new PatternAccessSession.ProviderDirectoryEntry(provider, 0, 0, group, 2,
            true, false, false, null, false, 0, 0, -1);
        var decoded = new AtomicInteger();
        IPatternDetails pattern = new IPatternDetails() {
            public AEItemKey getDefinition() {
                return AEItemKey.of(Items.APPLE);
            }

            public IInput[] getInputs() {
                return new IInput[0];
            }

            public List<GenericStack> getOutputs() {
                return List.of(new GenericStack(AEItemKey.of(Items.APPLE), 1));
            }
        };
        var tracker = new PatternAccessSession.ContainerTracker(directory, null, (stack, level) -> {
            decoded.incrementAndGet();
            return pattern;
        }, 1);
        assertEquals(0, decoded.get());
        tracker.synchronizeClientSnapshot();
        assertEquals(2, decoded.get());
        if (inventory instanceof VersionedInventory measured) {
            measured.reads = 0;
        }
        for (int i = 0; i < 20; i++) {
            assertNull(tracker.createUpdatePacket());
        }
        assertEquals(2, decoded.get());
        if (inventory instanceof VersionedInventory measured) {
            assertEquals(0, measured.reads);
        }
        inventory.getStackInSlot(0).setTagCompound(new NBTTagCompound());
        inventory.getStackInSlot(0).getTagCompound().setInteger("changed", 1);
        inventory.setItemDirect(0, inventory.getStackInSlot(0));
        assertNotNull(tracker.createUpdatePacket());
        tracker.synchronizeClientSnapshot();
        assertNull(tracker.createUpdatePacket());
        assertEquals(3, decoded.get());
        inventory.setItemDirect(0, ItemStack.EMPTY);
        assertNotNull(tracker.createUpdatePacket());
        tracker.synchronizeClientSnapshot();
        assertNull(tracker.createUpdatePacket());
        assertEquals(3, decoded.get());
    }

    private static final class VersionedInventory extends AppEngInternalInventory
        implements VersionedInternalInventory {
        private long version;
        private int reads;

        private VersionedInventory() {
            super(2);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            this.reads++;
            return super.getStackInSlot(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            this.version++;
            super.onContentsChanged(slot);
        }

        @Override
        public long getContentsVersion() {
            return this.version;
        }
    }
}
