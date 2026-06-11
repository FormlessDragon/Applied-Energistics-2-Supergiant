package ae2.core.network.serverbound;

import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.stacks.GenericStack;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.common.MEIngredientAction;
import ae2.container.me.common.MEIngredientActions;
import ae2.core.network.NetworkPacketHelper;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class HeiIngredientActionPacket extends ServerboundPacket {
    private static final int MAX_GENERIC_STACK_PAYLOAD_BYTES = 32 * 1024;

    private MEIngredientAction action;
    private GenericStack stack;

    public HeiIngredientActionPacket() {
    }

    public HeiIngredientActionPacket(MEIngredientAction action, GenericStack stack) {
        this.action = action;
        this.stack = stack;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.action = NetworkPacketHelper.readEnumOrNull(packetBuffer, MEIngredientAction.class);
        if (buf.readableBytes() > MAX_GENERIC_STACK_PAYLOAD_BYTES) {
            this.invalidateMalformed(buf, new IllegalArgumentException("GenericStack payload too large"));
            return;
        }

        try {
            this.stack = GenericStack.readBuffer(packetBuffer);
        } catch (RuntimeException e) {
            this.invalidateMalformed(buf, e);
            return;
        }
        if (packetBuffer.isReadable()) {
            this.invalidateMalformed(packetBuffer, new IllegalArgumentException(
                "Trailing HEI ingredient action packet payload bytes: " + packetBuffer.readableBytes()));
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeEnumValue(this.action);
        GenericStack.writeBuffer(this.stack, packetBuffer);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.isInvalid() || this.action == null || this.stack == null || this.stack.amount() <= 0
            || this.stack.amount() > PatternDetailsHelper.MAX_PROCESSING_PATTERN_AMOUNT) {
            return;
        }

        if (player.openContainer instanceof ContainerMEStorage container
            && MEIngredientActions.handleContainer(container, player, this.action, this.stack)) {
            return;
        }

        MEIngredientActions.handleWirelessTerminals(player, this.action, this.stack);
    }
}
