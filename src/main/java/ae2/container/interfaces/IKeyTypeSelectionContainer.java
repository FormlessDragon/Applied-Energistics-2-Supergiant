package ae2.container.interfaces;

import ae2.api.stacks.AEKeyType;
import ae2.api.util.KeyTypeSelection;
import ae2.container.guisync.PacketWritable;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SelectKeyTypePacket;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

public interface IKeyTypeSelectionContainer {
    KeyTypeSelection getServerKeyTypeSelection();

    SyncedKeyTypes getClientKeyTypeSelection();

    @ApiStatus.NonExtendable
    default void selectKeyType(AEKeyType keyType, boolean enabled) {
        InitNetwork.sendToServer(new SelectKeyTypePacket(keyType, enabled));
        getClientKeyTypeSelection().keyTypes().put(keyType, enabled);
    }

    record SyncedKeyTypes(Object2BooleanMap<AEKeyType> keyTypes) implements PacketWritable {
        public SyncedKeyTypes() {
            this(new Object2BooleanLinkedOpenHashMap<>());
        }

        public SyncedKeyTypes(Object2BooleanMap<AEKeyType> keyTypes) {
            this.keyTypes = new Object2BooleanLinkedOpenHashMap<>(keyTypes);
        }

        public SyncedKeyTypes(ByteBuf buf) {
            this(new Object2BooleanLinkedOpenHashMap<>());
            var packetBuffer = new PacketBuffer(buf);
            int size = packetBuffer.readVarInt();
            for (int i = 0; i < size; i++) {
                var keyType = AEKeyType.fromRawId(packetBuffer.readVarInt());
                var enabled = packetBuffer.readBoolean();
                if (keyType != null) {
                    this.keyTypes.put(keyType, enabled);
                }
            }
        }

        @Override
        public void writeToPacket(ByteBuf buf) {
            var packetBuffer = new PacketBuffer(buf);
            packetBuffer.writeVarInt(this.keyTypes.size());
            for (Object2BooleanMap.Entry<AEKeyType> entry : this.keyTypes.object2BooleanEntrySet()) {
                packetBuffer.writeVarInt(entry.getKey().getRawId());
                packetBuffer.writeBoolean(entry.getBooleanValue());
            }
        }

        public List<AEKeyType> enabledSet() {
            var list = new ObjectArrayList<AEKeyType>();
            for (var typeEntry : this.keyTypes.object2BooleanEntrySet()) {
                if (typeEntry.getBooleanValue()) {
                    list.add(typeEntry.getKey());
                }
            }
            return list;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SyncedKeyTypes(Object2BooleanMap<AEKeyType> types))) {
                return false;
            }
            return Objects.equals(this.keyTypes, types);
        }

    }
}
