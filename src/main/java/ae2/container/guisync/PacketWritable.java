package ae2.container.guisync;

import io.netty.buffer.ByteBuf;

/**
 * Implement on classes to signal they can be synchronized to the client using {@link GuiSync}. For this to work
 * fully, the class also needs to have a public constructor that takes a {@link ByteBuf} argument.
 */
public interface PacketWritable {
    void writeToPacket(ByteBuf data);
}

