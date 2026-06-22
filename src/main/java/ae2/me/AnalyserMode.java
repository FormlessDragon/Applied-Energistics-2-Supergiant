package ae2.me;

import ae2.core.localization.GuiText;
import net.minecraft.util.text.ITextComponent;

public enum AnalyserMode {
    FULL,
    NODES,
    CHANNELS,
    NONUM,
    P2P;

    private static final AnalyserMode[] VALUES = values();

    public static AnalyserMode byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }

    public ITextComponent getTranslatedName() {
        return GuiText.networkAnalyserMode(this).text();
    }
}
