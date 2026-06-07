package ae2.integration.abstraction;

import net.minecraft.util.EnumFacing;

import java.util.Set;

public interface IC2PowerSink {

    default void invalidate() {
    }

    default void onChunkUnload() {
    }

    default void onLoad() {
    }

    default void setValidFaces(Set<EnumFacing> faces) {
    }
}
