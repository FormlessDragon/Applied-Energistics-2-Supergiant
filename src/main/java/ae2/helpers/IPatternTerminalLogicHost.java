package ae2.helpers;

import ae2.parts.encoding.PatternEncodingLogic;
import net.minecraft.world.World;

public interface IPatternTerminalLogicHost {
    PatternEncodingLogic getLogic();

    World getLevel();

    void markForSave();
}
