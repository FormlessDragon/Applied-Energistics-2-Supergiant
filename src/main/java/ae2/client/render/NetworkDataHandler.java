package ae2.client.render;

import ae2.items.tools.NetworkAnalyserConfig;
import ae2.me.AnalyserMode;
import ae2.me.NetworkData;
import ae2.util.ColorData;

public final class NetworkDataHandler {
    private static NetworkData data = NetworkData.EMPTY;
    private static NetworkAnalyserConfig config = NetworkAnalyserConfig.DEFAULT;

    private NetworkDataHandler() {
    }

    public static void receiveData(NetworkData newData) {
        data = newData == null || newData.isCorrupt() ? NetworkData.EMPTY : newData;
    }

    public static NetworkData pullData() {
        return data;
    }

    public static void updateConfig(NetworkAnalyserConfig newConfig) {
        config = newConfig == null ? NetworkAnalyserConfig.DEFAULT : newConfig;
    }

    public static AnalyserMode getMode() {
        return config.mode();
    }

    public static float getNodeSize() {
        return config.nodeSize();
    }

    public static ColorData getColorByConfig(Enum<?> key) {
        ColorData color = config.colors().get(key);
        return color != null ? color : NetworkAnalyserConfig.DEFAULT_COLORS.get(key);
    }
}
