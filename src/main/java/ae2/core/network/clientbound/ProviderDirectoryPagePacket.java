package ae2.core.network.clientbound;

import ae2.api.stacks.AEItemKey;
import ae2.container.me.patternencode.ProviderDirectoryPage;
import ae2.container.me.patternencode.ProviderPageLimits;
import ae2.core.network.ClientboundPacket;
import ae2.core.network.NetworkPacketHelper;
import ae2.core.worlddata.PatternProviderMappingData;
import ae2.crafting.pattern.RecipeTypeUid;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProviderDirectoryPagePacket extends ClientboundPacket {
    @Nullable
    private ProviderDirectoryPage page;
    private boolean malformed;

    public ProviderDirectoryPagePacket() {
    }

    public ProviderDirectoryPagePacket(ProviderDirectoryPage page) {
        this.page = Objects.requireNonNull(page, "page");
    }

    @Override
    protected void read(ByteBuf buf) {
        this.page = null;
        this.malformed = false;
        try {
            if (buf.readableBytes() > ProviderPageLimits.MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Provider directory page packet exceeds "
                    + ProviderPageLimits.MAX_PACKET_BYTES + " bytes: " + buf.readableBytes());
            }

            PacketBuffer data = new PacketBuffer(buf);
            int windowId = data.readVarInt();
            long nonce = data.readVarLong();
            long directoryRevision = data.readVarLong();
            int pageIndex = data.readVarInt();
            int total = data.readVarInt();
            int entryCount = data.readVarInt();
            if (entryCount < 0 || entryCount > ProviderPageLimits.PAGE_SIZE) {
                throw new IllegalArgumentException("Invalid provider directory page entry count: " + entryCount);
            }

            List<ProviderDirectoryPage.Entry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                entries.add(readEntry(data));
            }
            if (data.isReadable()) {
                throw new IllegalArgumentException("Trailing provider directory page packet bytes: "
                    + data.readableBytes());
            }

            this.page = new ProviderDirectoryPage(
                windowId, nonce, directoryRevision, pageIndex, total, entries);
        } catch (RuntimeException e) {
            reject(buf, e);
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        ProviderDirectoryPage currentPage = Objects.requireNonNull(this.page, "page");
        ByteBuf encoded = Unpooled.buffer();
        try {
            PacketBuffer data = new PacketBuffer(encoded);
            data.writeVarInt(currentPage.windowId());
            data.writeVarLong(currentPage.nonce());
            data.writeVarLong(currentPage.directoryRevision());
            data.writeVarInt(currentPage.page());
            data.writeVarInt(currentPage.total());
            data.writeVarInt(currentPage.entries().size());
            for (ProviderDirectoryPage.Entry entry : currentPage.entries()) {
                writeEntry(data, entry);
            }

            int encodedBytes = encoded.readableBytes();
            if (encodedBytes > ProviderPageLimits.MAX_PACKET_BYTES) {
                IllegalArgumentException exception = new IllegalArgumentException(
                    "Provider directory page packet exceeds " + ProviderPageLimits.MAX_PACKET_BYTES
                        + " bytes: " + encodedBytes);
                NetworkPacketHelper.warnFailedPacket(
                    exception,
                    "provider-directory-page:encode-size",
                    "Refusing oversized provider directory page packet");
                throw exception;
            }
            buf.writeBytes(encoded, encoded.readerIndex(), encodedBytes);
        } finally {
            encoded.release();
        }
    }

    private static ProviderDirectoryPage.Entry readEntry(PacketBuffer data) {
        long providerId = data.readVarLong();
        AEItemKey icon = readIcon(data);
        String providerName = readBoundedString(
            data,
            "provider directory provider name",
            ProviderPageLimits.MAX_PROVIDER_NAME_UTF16_LENGTH,
            ProviderPageLimits.MAX_PROVIDER_NAME_UTF8_BYTES);
        int emptySlots = data.readVarInt();
        int recipeTypeCount = data.readVarInt();
        if (recipeTypeCount < 0) {
            throw new IllegalArgumentException("Invalid provider directory recipe type count: " + recipeTypeCount);
        }
        int recipeTypeUidCount = data.readVarInt();
        if (recipeTypeUidCount < 0
            || recipeTypeUidCount > PatternProviderMappingData.DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE
            || recipeTypeUidCount > recipeTypeCount) {
            throw new IllegalArgumentException("Invalid provider directory recipe type UID count: "
                + recipeTypeUidCount);
        }
        List<String> recipeTypeUids = new ArrayList<>(recipeTypeUidCount);
        for (int i = 0; i < recipeTypeUidCount; i++) {
            recipeTypeUids.add(RecipeTypeUid.requireValid(data.readString(RecipeTypeUid.MAX_UTF16_LENGTH)));
        }
        boolean acceptsProcessingPatterns = data.readBoolean();
        boolean hasLocation = data.readBoolean();
        int locationDimension = 0;
        long locationPos = 0;
        int locationSide = -1;
        if (hasLocation) {
            locationDimension = data.readInt();
            locationPos = data.readLong();
            locationSide = data.readByte();
        }
        return new ProviderDirectoryPage.Entry(
            providerId,
            icon,
            providerName,
            emptySlots,
            recipeTypeCount,
            recipeTypeUids,
            acceptsProcessingPatterns,
            hasLocation,
            locationDimension,
            locationPos,
            locationSide);
    }

    private static void writeEntry(PacketBuffer data, ProviderDirectoryPage.Entry entry) {
        data.writeVarLong(entry.providerId());
        writeIcon(data, entry.providerId(), entry.icon());
        writeBoundedString(
            data,
            "provider directory provider name",
            entry.providerName(),
            ProviderPageLimits.MAX_PROVIDER_NAME_UTF16_LENGTH,
            ProviderPageLimits.MAX_PROVIDER_NAME_UTF8_BYTES);
        data.writeVarInt(entry.emptySlots());
        data.writeVarInt(entry.recipeTypeCount());
        data.writeVarInt(entry.recipeTypeUids().size());
        for (String recipeTypeUid : entry.recipeTypeUids()) {
            data.writeString(RecipeTypeUid.requireValid(recipeTypeUid));
        }
        data.writeBoolean(entry.acceptsProcessingPatterns());
        data.writeBoolean(entry.hasLocation());
        if (entry.hasLocation()) {
            data.writeInt(entry.locationDimension());
            data.writeLong(entry.locationPos());
            data.writeByte(entry.locationSide());
        }
    }

    @Nullable
    private static AEItemKey readIcon(PacketBuffer data) {
        int encodedBytes = data.readVarInt();
        if (encodedBytes < 0 || encodedBytes > ProviderPageLimits.MAX_ICON_BYTES
            || encodedBytes > data.readableBytes()) {
            throw new IllegalArgumentException("Invalid provider directory icon payload length: " + encodedBytes);
        }
        if (encodedBytes == 0) {
            return null;
        }

        byte[] encoded = new byte[encodedBytes];
        data.readBytes(encoded);
        ByteBuf iconBuffer = Unpooled.wrappedBuffer(encoded);
        try {
            PacketBuffer iconData = new PacketBuffer(iconBuffer);
            AEItemKey icon = AEItemKey.fromPacket(iconData);
            if (icon == null) {
                throw new IllegalArgumentException("Provider directory icon decoded to an empty item");
            }
            if (iconData.isReadable()) {
                throw new IllegalArgumentException("Trailing provider directory icon payload bytes: "
                    + iconData.readableBytes());
            }
            return icon;
        } finally {
            iconBuffer.release();
        }
    }

    private static void writeIcon(PacketBuffer data, long providerId, @Nullable AEItemKey icon) {
        byte[] encodedIcon = encodeIcon(providerId, icon);
        if (encodedIcon == null) {
            data.writeVarInt(0);
            return;
        }
        data.writeVarInt(encodedIcon.length);
        data.writeBytes(encodedIcon);
    }

    private static byte @Nullable [] encodeIcon(long providerId, @Nullable AEItemKey icon) {
        if (icon == null) {
            return null;
        }

        ByteBuf iconBuffer = Unpooled.buffer(256, ProviderPageLimits.MAX_ICON_BYTES + 1);
        try {
            icon.writeToPacket(new PacketBuffer(iconBuffer));
            int encodedBytes = iconBuffer.readableBytes();
            if (encodedBytes > ProviderPageLimits.MAX_ICON_BYTES) {
                IllegalArgumentException exception = new IllegalArgumentException(
                    "Provider directory icon exceeds " + ProviderPageLimits.MAX_ICON_BYTES
                        + " bytes: " + encodedBytes);
                warnOmittedIcon(exception, providerId);
                return null;
            }

            byte[] encoded = new byte[encodedBytes];
            iconBuffer.getBytes(iconBuffer.readerIndex(), encoded);
            return encoded;
        } catch (RuntimeException e) {
            warnOmittedIcon(e, providerId);
            return null;
        } finally {
            iconBuffer.release();
        }
    }

    private static void warnOmittedIcon(RuntimeException exception, long providerId) {
        NetworkPacketHelper.warnFailedPacket(
            exception,
            "provider-directory-page:icon",
            "Omitting provider directory icon that could not fit the packet budget for provider %s",
            providerId);
    }

    private static String readBoundedString(PacketBuffer data, String fieldName, int maxUtf16Length,
                                            int maxUtf8Bytes) {
        return ProviderPageLimits.requireBoundedText(
            fieldName,
            data.readString(maxUtf16Length),
            maxUtf16Length,
            maxUtf8Bytes);
    }

    private static void writeBoundedString(PacketBuffer data, String fieldName, String value, int maxUtf16Length,
                                           int maxUtf8Bytes) {
        data.writeString(ProviderPageLimits.requireBoundedText(
            fieldName, value, maxUtf16Length, maxUtf8Bytes));
    }

    private void reject(ByteBuf buf, RuntimeException exception) {
        this.page = null;
        this.malformed = true;
        if (buf.isReadable()) {
            buf.skipBytes(buf.readableBytes());
        }
        NetworkPacketHelper.warnMalformedPacket(
            exception,
            "provider-directory-page",
            "Ignoring malformed provider directory page packet");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (this.malformed || this.page == null || minecraft.player == null
            || minecraft.player.openContainer == null) {
            return;
        }
        if (minecraft.currentScreen instanceof IProviderSelectPageReceiver receiver) {
            dispatchToReceiver(receiver, minecraft.player.openContainer.windowId, this.page);
        }
    }

    static void dispatchToReceiver(IProviderSelectPageReceiver receiver, int openWindowId,
                                   ProviderDirectoryPage page) {
        if (openWindowId == page.windowId()) {
            receiver.receiveProviderDirectoryPage(page);
        }
    }

    @Nullable
    public ProviderDirectoryPage page() {
        return this.page;
    }

    public boolean isMalformed() {
        return this.malformed;
    }
}
