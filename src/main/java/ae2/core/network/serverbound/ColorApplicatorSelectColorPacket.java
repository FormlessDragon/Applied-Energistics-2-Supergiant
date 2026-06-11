package ae2.core.network.serverbound;

import ae2.api.util.AEColor;
import ae2.core.network.NetworkPacketHelper;
import ae2.core.network.ServerboundPacket;
import ae2.items.tools.powered.ColorApplicatorItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import org.jetbrains.annotations.Nullable;

public class ColorApplicatorSelectColorPacket extends ServerboundPacket {

    @Nullable
    private AEColor color;
    private boolean invalidRequest;

    public ColorApplicatorSelectColorPacket() {
    }

    public ColorApplicatorSelectColorPacket(@Nullable AEColor color) {
        this.color = color;
    }

    private static void switchColor(ItemStack stack, @Nullable AEColor color) {
        if (!stack.isEmpty() && stack.getItem() instanceof ColorApplicatorItem colorApplicator) {
            colorApplicator.setActiveColor(stack, color);
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        if (data.readBoolean()) {
            this.color = NetworkPacketHelper.readEnumOrNull(data, AEColor.class);
            this.invalidRequest = this.color == null;
        } else {
            this.color = null;
            this.invalidRequest = false;
        }
        if (data.isReadable()) {
            throw new IllegalArgumentException("Trailing color applicator selection packet payload bytes: "
                + data.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        PacketBuffer data = new PacketBuffer(buf);
        data.writeBoolean(this.color != null);
        if (this.color != null) {
            data.writeEnumValue(this.color);
        }
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (this.invalidRequest) {
            return;
        }
        switchColor(player.getHeldItemMainhand(), this.color);
        switchColor(player.getHeldItemOffhand(), this.color);
    }
}
