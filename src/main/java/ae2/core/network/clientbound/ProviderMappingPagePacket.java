package ae2.core.network.clientbound;

import ae2.container.me.patternencode.ProviderMappingPage;
import ae2.container.me.patternencode.ProviderPageLimits;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.NetworkPacketHelper;
import ae2.crafting.pattern.RecipeTypeUid;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Clientbound, bounded mapping-management page. Discriminator 66 remains deliberately unused. */
public final class ProviderMappingPagePacket extends ClientboundPacket {
    private ProviderMappingPage page;
    private boolean malformed;

    public ProviderMappingPagePacket() {
    }

    public ProviderMappingPagePacket(ProviderMappingPage page) {
        this.page = Objects.requireNonNull(page, "page");
    }

    @Override
    protected void read(ByteBuf buf) {
        this.page = null;
        this.malformed = false;
        try {
            if (buf.readableBytes() > ProviderPageLimits.MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Provider mapping page packet exceeds size limit");
            }
            PacketBuffer data = new PacketBuffer(buf);
            int windowId = data.readVarInt();
            long nonce = data.readVarLong();
            long revision = data.readVarLong();
            long providerId = data.readVarLong();
            int pageIndex = data.readVarInt();
            int total = data.readVarInt();
            int count = data.readVarInt();
            if (count < 0 || count > ProviderPageLimits.PAGE_SIZE) {
                throw new IllegalArgumentException("Invalid provider mapping page entry count: " + count);
            }
            List<String> recipeTypeUids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                recipeTypeUids.add(RecipeTypeUid.requireValid(data.readString(RecipeTypeUid.MAX_UTF16_LENGTH)));
            }
            if (data.isReadable()) {
                throw new IllegalArgumentException("Trailing provider mapping page bytes");
            }
            this.page = new ProviderMappingPage(windowId, nonce, revision, providerId, pageIndex, total,
                recipeTypeUids);
        } catch (RuntimeException e) {
            this.malformed = true;
            if (buf.isReadable()) {
                buf.skipBytes(buf.readableBytes());
            }
            NetworkPacketHelper.warnMalformedPacket(e, "provider-mapping-page",
                "Ignoring malformed provider mapping page packet");
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        ProviderMappingPage current = Objects.requireNonNull(this.page, "page");
        ByteBuf encoded = Unpooled.buffer();
        try {
            PacketBuffer data = new PacketBuffer(encoded);
            data.writeVarInt(current.windowId());
            data.writeVarLong(current.nonce());
            data.writeVarLong(current.directoryRevision());
            data.writeVarLong(current.providerId());
            data.writeVarInt(current.page());
            data.writeVarInt(current.total());
            data.writeVarInt(current.recipeTypeUids().size());
            for (String uid : current.recipeTypeUids()) {
                data.writeString(RecipeTypeUid.requireValid(uid));
            }
            if (encoded.readableBytes() > ProviderPageLimits.MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Provider mapping page packet exceeds size limit");
            }
            buf.writeBytes(encoded, encoded.readerIndex(), encoded.readableBytes());
        } finally {
            encoded.release();
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (!this.malformed && this.page != null && minecraft.player != null
            && minecraft.currentScreen instanceof IProviderSelectPageReceiver receiver
            && minecraft.player.openContainer.windowId == this.page.windowId()) {
            receiver.receiveProviderMappingPage(this.page);
        }
    }
}
