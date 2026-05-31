package ae2.helpers;

import ae2.api.storage.ITerminalHost;
import ae2.parts.encoding.PatternEncodingLogic;

public interface IPatternTerminalGuiHost extends ITerminalHost {
    PatternEncodingLogic getLogic();
}
