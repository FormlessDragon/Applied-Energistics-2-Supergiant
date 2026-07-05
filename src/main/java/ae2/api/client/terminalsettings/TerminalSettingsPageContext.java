package ae2.api.client.terminalsettings;

import ae2.client.gui.AEBaseGui;
import ae2.container.AEBaseContainer;
import ae2.helpers.WirelessTerminalGuiHost;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Host-provided context for terminal settings pages.
 * <p>
 * Coordinates passed to creation methods are relative to the terminal settings GUI content area. The host keeps these
 * controls scoped to the page that created them.
 */
@SideOnly(Side.CLIENT)
public interface TerminalSettingsPageContext {

    /**
     * @return The screen that opened terminal settings.
     */
    AEBaseGui<?> parentGui();

    /**
     * @return The active container backing terminal settings.
     */
    AEBaseContainer container();

    /**
     * @return Player inventory for the active container.
     */
    InventoryPlayer playerInventory();

    /**
     * @return Wireless host when the settings screen was opened from a wireless terminal.
     */
    @Nullable
    WirelessTerminalGuiHost<?> wirelessHost();

    /**
     * @return True when this settings screen should only show wireless terminal pages.
     */
    boolean wirelessOnly();

    /**
     * Adds a checkbox to the current page.
     */
    TerminalSettingsCheckbox addCheckbox(String id, int x, int y, int width, ITextComponent text,
                                         Runnable changeListener);

    /**
     * Adds a text field to the current page.
     */
    TerminalSettingsTextField addTextField(String id, int x, int y, int width, int height);

    /**
     * Adds a plain text button to the current page.
     */
    TerminalSettingsButton addButton(String id, int x, int y, int width, int height, ITextComponent text,
                                     Runnable action);

    /**
     * Adds a label to the current page.
     */
    TerminalSettingsLabel addLabel(String id, int x, int y, ITextComponent text);

    interface TerminalSettingsControl {
        void setVisible(boolean visible);

        void setEnabled(boolean enabled);
    }

    interface TerminalSettingsCheckbox extends TerminalSettingsControl {
        boolean isSelected();

        void setSelected(boolean selected);

        void setRadio(boolean radio);

        void setTooltipMessage(List<ITextComponent> tooltip);
    }

    interface TerminalSettingsTextField extends TerminalSettingsControl {
        String getText();

        void setText(String text);

        boolean isFocused();

        void setFocused(boolean focused);

        void setMaxStringLength(int length);

        void setTextColor(int color);

        void setTooltipMessage(List<ITextComponent> tooltip);

        void setKeyFilter(@Nullable BiPredicate<Character, Integer> keyFilter);

        void setResponder(@Nullable Consumer<String> responder);
    }

    interface TerminalSettingsButton extends TerminalSettingsControl {
    }

    interface TerminalSettingsLabel extends TerminalSettingsControl {
        void setText(ITextComponent text);
    }
}
