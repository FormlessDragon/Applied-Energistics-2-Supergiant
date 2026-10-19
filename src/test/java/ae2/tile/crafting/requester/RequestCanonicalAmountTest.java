package ae2.tile.crafting.requester;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestCanonicalAmountTest {
    @Test
    void fullLongAmountsAndBatchSizesAreRetainedWhileLowerBoundsStillApply() {
        var request = new Request();
        request.updateConfiguredStack(new GenericStack(new TestKey(), Long.MAX_VALUE));
        request.updateBatchSize(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, request.getAmount());
        assertEquals(Long.MAX_VALUE, request.getConfiguredStack().amount());
        assertEquals(Long.MAX_VALUE, request.getBatchSize());

        request.updateAmount(Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE - 1, request.getAmount());
        assertEquals(Long.MAX_VALUE - 1, request.getConfiguredStack().amount());

        request.setAmount(Long.MIN_VALUE);
        request.setBatchSize(Long.MIN_VALUE);
        assertEquals(0, request.getAmount());
        assertEquals(1, request.getBatchSize());
    }

    @Test
    void amountUpdateKeepsConfiguredStackAndWrittenTagCanonical() {
        var request = new Request();
        request.updateConfiguredStack(new GenericStack(new TestKey(), 1));
        request.updateAmount(2);

        assertEquals(2, request.getAmount());
        assertEquals(2, request.getConfiguredStack().amount());
    }

    private static final class TestKey extends AEKey {
        private static final AEKeyType TYPE = new AEKeyType(
            new ResourceLocation("ae2", "request-test"), TestKey.class, new TextComponentString("test")) {
            @Override
            public AEKey readFromPacket(PacketBuffer input) {
                return null;
            }

            @Override
            public AEKey loadKeyFromTag(NBTTagCompound tag) {
                return null;
            }
        };

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public NBTTagCompound toTag() {
            return new NBTTagCompound();
        }

        @Override
        public Object getPrimaryKey() {
            return TYPE;
        }

        @Override
        public ResourceLocation getId() {
            return TYPE.getId();
        }

        @Override
        public void writeToPacket(PacketBuffer data) {
        }

        @Override
        public Object getReadOnlyStack() {
            return this;
        }

        @Override
        protected ITextComponent computeDisplayName() {
            return new TextComponentString("test");
        }

        @Override
        public NBTBase get(String componentId) {
            return null;
        }
    }
}
