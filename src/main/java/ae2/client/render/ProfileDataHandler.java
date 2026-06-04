package ae2.client.render;

import ae2.me.ticker.ProfileData;
import ae2.items.tools.TickAnalyserConfig;

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
        return rate < 5.0 && config.op1()
            || rate >= 5.0 && rate < 100.0 && config.op2()
            || rate >= 100.0 && rate < 500.0 && config.op3()
            || rate >= 500.0 && config.op4();
    }
}
