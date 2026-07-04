package ae2.client.gui.implementations;

import ae2.api.config.IncludeExclude;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.DynamicIconButton;
import ae2.client.gui.widgets.SimpleIconButton;
import ae2.container.implementations.ContainerWirelessMagnet;
import ae2.core.localization.GuiText;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GuiWirelessMagnet extends AEBaseGui<ContainerWirelessMagnet> {
    private static final String STYLE_PATH = "/screens/wireless_magnet.json";

    private final DynamicIconButton pickupMode;
    private final DynamicIconButton insertMode;

    public GuiWirelessMagnet(ContainerWirelessMagnet container, InventoryPlayer playerInventory,
                             ITextComponent title) {
        this(container, playerInventory, title, GuiStyleManager.loadStyleDoc(STYLE_PATH));
    }

    private GuiWirelessMagnet(ContainerWirelessMagnet container, InventoryPlayer playerInventory,
                              @Nullable ITextComponent title, GuiStyle style) {
        super(container, playerInventory, style);
        AESubGui.addBackButton(container, "back", widgets);
        setTextContent(TEXT_ID_DIALOG_TITLE, title != null ? title : GuiText.WirelessMagnetTitle.text());

        this.pickupMode = new DynamicIconButton(
            () -> container.getPickupMode() == IncludeExclude.WHITELIST ? Icon.WHITELIST : Icon.BLACKLIST,
            GuiText.WirelessMagnetPickupWhitelist.text(),
            this::pickupModeTooltip,
            this::togglePickupMode);
        this.insertMode = new DynamicIconButton(
            () -> container.getInsertMode() == IncludeExclude.WHITELIST ? Icon.WHITELIST : Icon.BLACKLIST,
            GuiText.WirelessMagnetInsertWhitelist.text(),
            this::insertModeTooltip,
            this::toggleInsertMode);
        widgets.add("pickup_mode", this.pickupMode);
        widgets.add("insert_mode", this.insertMode);
        widgets.add("copy_up", new SimpleIconButton(Icon.ARROW_UP, GuiText.WirelessMagnetCopyPickup.text(),
            container::copyInsertToPickup));
        widgets.add("copy_down", new SimpleIconButton(Icon.ARROW_DOWN, GuiText.WirelessMagnetCopyInsert.text(),
            container::copyPickupToInsert));
        widgets.add("switch", new SimpleIconButton(Icon.CYCLE, GuiText.WirelessMagnetSwap.text(),
            container::swapConfigs));
    }

    private void togglePickupMode() {
        container.togglePickupMode();
    }

    private void toggleInsertMode() {
        container.toggleInsertMode();
    }

    private List<ITextComponent> pickupModeTooltip() {
        return container.getPickupMode() == IncludeExclude.WHITELIST
            ? List.of(GuiText.WirelessMagnetPickupWhitelist.text())
            : List.of(GuiText.WirelessMagnetPickupBlacklist.text());
    }

    private List<ITextComponent> insertModeTooltip() {
        return container.getInsertMode() == IncludeExclude.WHITELIST
            ? List.of(GuiText.WirelessMagnetInsertWhitelist.text())
            : List.of(GuiText.WirelessMagnetInsertBlacklist.text());
    }
}
