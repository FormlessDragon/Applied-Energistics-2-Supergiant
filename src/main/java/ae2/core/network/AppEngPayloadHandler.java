package ae2.core.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

public final class AppEngPayloadHandler {

    private AppEngPayloadHandler() {
    }

    public static final class Client<T extends ClientboundPacket> implements IMessageHandler<T, IMessage> {
        @Override
        public @Nullable IMessage onMessage(T message, MessageContext ctx) {
            runClient(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void runClient(T message) {
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.addScheduledTask(() -> message.handleClient(minecraft));
        }
    }

    public static final class Server<T extends ServerboundPacket> implements IMessageHandler<T, IMessage> {
        @Override
        public @Nullable IMessage onMessage(T message, MessageContext ctx) {
            if (message.isInvalid()) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.isInvalid()) {
                    return;
                }
                try {
                    message.handleServer(player);
                } catch (RuntimeException e) {
                    String packetName = message.getClass().getSimpleName();
                    NetworkPacketHelper.warnFailedPacket(e, packetName + ":" + player.getUniqueID(),
                        "Ignoring failed serverbound packet %s from %s",
                        packetName, player.getName());
                }
            });
            return null;
        }
    }
}
