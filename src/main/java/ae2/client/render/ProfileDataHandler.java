package ae2.client.render;

import ae2.items.tools.TickAnalyserConfig;
import ae2.me.ticker.ProfileData;

public final class ProfileDataHandler {
    private static ProfileData data = ProfileData.EMPTY;
    private static TickAnalyserConfig config = TickAnalyserConfig.DEFAULT;

    private ProfileDataHandler() {
    }

    public static void receiveData(ProfileData newData) {
        data = newData == null || newData.isCorrupt() ? ProfileData.EMPTY : newData;
    }

    public static ProfileData pullData() {
        return data;
    }

    public static void updateConfig(TickAnalyserConfig newConfig) {
        config = newConfig == null ? TickAnalyserConfig.DEFAULT : newConfig;
    }

    public static boolean shouldRender(double rate) {
        return (rate < 5.0 && config.showBelow5Micros())
            || (rate >= 5.0 && rate < 100.0 && config.show5To100Micros())
            || (rate >= 100.0 && rate < 500.0 && config.show100To500Micros())
            || (rate >= 500.0 && config.showAbove500Micros());
    }
}
