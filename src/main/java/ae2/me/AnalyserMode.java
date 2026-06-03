package ae2.me;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

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
        return new TextComponentTranslation("gui.ae2.network_analyser.mode." + name());
    }
}
