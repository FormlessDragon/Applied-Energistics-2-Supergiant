package ae2.client.gui.widgets;

import net.minecraft.client.gui.GuiButton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalButtonBarHeightTest {
    @Test
    void emptyOrHiddenToolbarHasNoMinimumHeight() {
        var toolbar = new VerticalButtonBar();
        assertEquals(0, toolbar.getMinimumHeight());

        var button = new GuiButton(0, 0, 0, 16, 16, "");
        button.visible = false;
        toolbar.add(button);
        assertEquals(0, toolbar.getMinimumHeight());
    }

    @Test
    void measuresVisibleButtonHeightsBeforeFirstLayout() {
        var toolbar = new VerticalButtonBar();
        toolbar.add(new GuiButton(0, 0, 0, 16, 16, ""));
        var button = new GuiButton(1, 0, 0, 16, 24, "");
        toolbar.add(button);

        assertEquals(58, toolbar.getMinimumHeight());

        toolbar.updateBeforeRender();
        assertTrue(toolbar.getMinimumHeight() > toolbar.getBounds().height + 3);

        button.visible = false;
        assertEquals(28, toolbar.getMinimumHeight());
    }
}
