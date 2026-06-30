package ae2.api.storage;

import ae2.helpers.IPatternTerminalGuiHost;

/**
 * Host contract for the pattern encoding access terminal container.
 * <p>
 * The PEA terminal needs both pattern-provider access behavior and pattern encoding behavior in the same GUI host:
 * {@link IPatternAccessTermContainerHost} supplies the configurable pattern-provider visibility, grid node and link
 * status members used by access-terminal views, while {@link IPatternTerminalGuiHost} supplies the terminal inventory
 * contract and {@code PatternEncodingLogic} access used by encoding-terminal screens.
 * <p>
 * This interface exists so PEA containers can request one explicit host type instead of accepting unrelated hosts that
 * only implement one side of the terminal behavior.
 */
public interface IPEATermContainerHost extends IPatternAccessTermContainerHost, IPatternTerminalGuiHost {
}
