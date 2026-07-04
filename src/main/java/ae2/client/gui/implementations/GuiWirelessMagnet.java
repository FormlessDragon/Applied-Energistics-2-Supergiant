package ae2.client.gui.implementations;

import ae2.api.config.IncludeExclude;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.IconButton;
import ae2.container.implementations.ContainerWirelessMagnet;
import ae2.core.localization.GuiText;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class GuiWirelessMagnet extends AEBaseGui<ContainerWirelessMagnet> {
    private static final String STYLE_PATH = "/screens/wireless_magnet.json";

    private final MagnetModeButton pickupMode;
    private final MagnetModeButton insertMode;

    public GuiWirelessMagnet(ContainerWirelessMagnet container, InventoryPlayer playerInventory,
                             ITextComponent title) {
        this(container, playerInventory, title, GuiStyleManager.loadStyleDoc(STYLE_PATH));
    }

    private GuiWirelessMagnet(ContainerWirelessMagnet container, InventoryPlayer playerInventory,
                              @Nullable ITextComponent title, GuiStyle style) {
        super(container, playerInventory, style);
        AESubGui.addBackButton(container, "back", widgets);
        setTextContent(TEXT_ID_DIALOG_TITLE, title != null ? title : GuiText.WirelessMagnetTitle.text());

        this.pickupMode = new MagnetModeButton(this::togglePickupMode);
        this.insertMode = new MagnetModeButton(this::toggleInsertMode);
        widgets.add("pickup_mode", this.pickupMode);
        widgets.add("insert_mode", this.insertMode);
        widgets.add("copy_up", new MagnetActionButton(Icon.ARROW_UP, GuiText.WirelessMagnetCopyPickup.text(),
            container::copyInsertToPickup));
        widgets.add("copy_down", new MagnetActionButton(Icon.ARROW_DOWN, GuiText.WirelessMagnetCopyInsert.text(),
            container::copyPickupToInsert));
        widgets.add("switch", new MagnetActionButton(Icon.CYCLE, GuiText.WirelessMagnetSwap.text(),
            container::swapConfigs));
        updateModeButtons();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateModeButtons();
    }

    private void updateModeButtons() {
        IncludeExclude pickupMode = container.getPickupMode();
        IncludeExclude insertMode = container.getInsertMode();
        this.pickupMode.setMode(pickupMode);
        this.pickupMode.setTooltip(pickupMode == IncludeExclude.WHITELIST
            ? List.of(GuiText.WirelessMagnetPickupWhitelist.text())
            : List.of(GuiText.WirelessMagnetPickupBlacklist.text()));
        this.insertMode.setMode(insertMode);
        this.insertMode.setTooltip(insertMode == IncludeExclude.WHITELIST
            ? List.of(GuiText.WirelessMagnetInsertWhitelist.text())
            : List.of(GuiText.WirelessMagnetInsertBlacklist.text()));
    }

    private void togglePickupMode() {
        container.togglePickupMode();
    }

    private void toggleInsertMode() {
        container.toggleInsertMode();
    }

    private static class MagnetActionButton extends IconButton {
        private final Icon icon;

        MagnetActionButton(Icon icon, ITextComponent message, Runnable onPress) {
            super(onPress);
            this.icon = icon;
            setMessage(message);
        }

        @Override
        protected Icon getIcon() {
            return this.icon;
        }
    }

    private static class MagnetModeButton extends IconButton {
        private IncludeExclude mode = IncludeExclude.BLACKLIST;
        private List<ITextComponent> tooltip = List.of();

        private MagnetModeButton(Runnable onPress) {
            super(onPress);
        }

        private void setMode(IncludeExclude mode) {
            this.mode = mode;
        }

        private void setTooltip(List<ITextComponent> tooltip) {
            this.tooltip = tooltip;
        }

        @Override
        protected Icon getIcon() {
            return this.mode == IncludeExclude.WHITELIST ? Icon.WHITELIST : Icon.BLACKLIST;
        }

        @Override
        public @NonNull List<ITextComponent> getTooltipMessage() {
            return this.tooltip;
        }
    }
}
