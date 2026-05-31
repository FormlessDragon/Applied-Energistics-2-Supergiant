package ae2.util;

import ae2.api.networking.IGridNode;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;

import java.io.IOException;

public interface IDebugExportable {
    void debugExport(JsonWriter writer, Reference2IntMap<Object> machineIds,
                     Reference2IntMap<IGridNode> nodeIds) throws IOException;
}
