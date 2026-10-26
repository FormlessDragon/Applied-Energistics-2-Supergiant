package ae2.client.gui.style;

import ae2.client.Point;
import ae2.client.gui.widgets.VerticalButtonBar;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalStyleHeightTest {
    @ParameterizedTest
    @CsvSource({"0, 2", "152, 2", "153, 3", "170, 3", "171, 4", "188, 4", "189, 5"})
    void roundsMinimumHeightUpToCompleteRows(int minimumHeight, int expectedRows) {
        var style = terminalStyle(17, 18, 18, 99);

        int rows = style.getMinimumRows(minimumHeight);

        assertEquals(expectedRows, rows);
        assertTrue(style.getScreenHeight(rows) >= minimumHeight);
        if (rows > 2) {
            assertTrue(style.getScreenHeight(rows - 1) < minimumHeight);
        }
    }

    @Test
    void accountsForDistinctFirstAndLastRowHeights() {
        var style = terminalStyle(17, 20, 22, 99);

        assertEquals(2, style.getMinimumRows(158));
        assertEquals(3, style.getMinimumRows(159));
        assertEquals(176, style.getScreenHeight(3));
    }

    @Test
    void mainPanelRemainsTallerThanToolbarForEveryTerminalStyle() {
        var style = terminalStyle(17, 18, 18, 99);
        for (int buttonCount = 1; buttonCount <= 12; buttonCount++) {
            var toolbar = new VerticalButtonBar();
            for (int i = 0; i < buttonCount; i++) {
                toolbar.add(new GuiButton(i, 0, 0, 16, 16, ""));
            }

            // The toolbar is anchored one pixel below the top of the main panel.
            toolbar.setPosition(new Point(3, 1));
            int minimumRows = style.getMinimumRows(1 + toolbar.getMinimumHeight());
            toolbar.updateBeforeRender();
            var bounds = toolbar.getBounds();
            int toolbarBottom = bounds.y - 1 + bounds.height + 4;

            for (var terminalStyle : ae2.api.config.TerminalStyle.values()) {
                int rows = Math.max(minimumRows, terminalStyle.getRows(10));
                assertTrue(style.getScreenHeight(rows) > toolbarBottom,
                    terminalStyle + " with " + buttonCount + " buttons");
            }
        }
    }

    private static TerminalStyle terminalStyle(int headerHeight, int firstRowHeight, int lastRowHeight,
                                                int bottomHeight) {
        var style = new TerminalStyle();
        style.setHeader(slice(headerHeight));
        style.setFirstRow(slice(firstRowHeight));
        style.setRow(slice(18));
        style.setLastRow(slice(lastRowHeight));
        style.setBottom(slice(bottomHeight));
        return style;
    }

    private static Blitter slice(int height) {
        return Blitter.texture(new ResourceLocation("ae2", "textures/guis/terminal.png"))
            .src(0, 0, 195, height);
    }
}
