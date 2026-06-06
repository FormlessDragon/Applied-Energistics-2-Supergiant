package ae2.me;

import ae2.core.localization.GuiText;
import net.minecraft.util.text.ITextComponent;

public enum AnalyserMode {
    FULL,
    NODES,
    CHANNELS,
    NONUM,
    P2P;

    public static AnalyserMode byIndex(int index) {
        AnalyserMode[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    public ITextComponent getTranslatedName() {
        return GuiText.networkAnalyserMode(this).text();
    }
}
