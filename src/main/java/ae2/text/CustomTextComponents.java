package ae2.text;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.network.PacketBuffer;

import java.util.Map;
import java.util.Objects;

public final class CustomTextComponents {
    private static final Map<String, Entry> ENTRIES = new Object2ObjectLinkedOpenHashMap<>();

    static {
        TextComponentItemStack.bootstrap();
    }

    private CustomTextComponents() {
    }

    public static void register(String typeId, JsonDecoder jsonDecoder, PacketDecoder packetDecoder) {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(jsonDecoder, "jsonDecoder");
        Objects.requireNonNull(packetDecoder, "packetDecoder");

        Entry previous = ENTRIES.putIfAbsent(typeId, new Entry(jsonDecoder, packetDecoder));
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate custom text component type id: " + typeId);
        }
    }

    public static ICustomTextComponent decodeJson(String typeId, JsonObject data) {
        return getEntry(typeId).jsonDecoder.decode(data);
    }

    public static ICustomTextComponent decodePacket(String typeId, PacketBuffer buffer) {
        return getEntry(typeId).packetDecoder.decode(buffer);
    }

    private static Entry getEntry(String typeId) {
        Entry entry = ENTRIES.get(typeId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown custom text component type id: " + typeId);
        }
        return entry;
    }

    @FunctionalInterface
    public interface JsonDecoder {
        ICustomTextComponent decode(JsonObject data);
    }

    @FunctionalInterface
    public interface PacketDecoder {
        ICustomTextComponent decode(PacketBuffer buffer);
    }

    private record Entry(JsonDecoder jsonDecoder, PacketDecoder packetDecoder) {
    }
}
