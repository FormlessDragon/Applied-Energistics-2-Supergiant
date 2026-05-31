package ae2.core.network.serverbound;

import ae2.api.stacks.GenericStack;
import ae2.container.me.common.ContainerMEStorage;
import ae2.container.me.common.MEIngredientAction;
import ae2.container.me.common.MEIngredientActions;
import ae2.core.network.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class HeiIngredientActionPacket extends ServerboundPacket {
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
        this.action = packetBuffer.readEnumValue(MEIngredientAction.class);
        this.stack = GenericStack.readBuffer(packetBuffer);
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeEnumValue(this.action);
        GenericStack.writeBuffer(this.stack, packetBuffer);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.action == null || this.stack == null) {
            return;
        }

        if (player.openContainer instanceof ContainerMEStorage container
            && MEIngredientActions.handleContainer(container, player, this.action, this.stack)) {
            return;
        }

        MEIngredientActions.handleWirelessTerminals(player, this.action, this.stack);
    }
}
