package ae2.client.gui.implementations;

import ae2.api.config.ActionItems;
import ae2.api.config.IncludeExclude;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.DynamicIconButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerPortableCellPickupFilter;
import ae2.core.localization.GuiText;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GuiPortableCellPickupFilter extends AEBaseGui<ContainerPortableCellPickupFilter> {
    private static final String STYLE_PATH = "/screens/portable_cell_pickup_filter.json";

    private final DynamicIconButton pickupMode;
    private final SettingToggleButton<YesNo> matchNbt;
    private final SettingToggleButton<YesNo> matchDamage;
    private final SettingToggleButton<YesNo> matchOreDictionary;

    public GuiPortableCellPickupFilter(ContainerPortableCellPickupFilter container, InventoryPlayer playerInventory,
                                       ITextComponent title) {
        this(container, playerInventory, title, GuiStyleManager.loadStyleDoc(STYLE_PATH));
    }

    private GuiPortableCellPickupFilter(ContainerPortableCellPickupFilter container, InventoryPlayer playerInventory,
                                        @Nullable ITextComponent title, GuiStyle style) {
        super(container, playerInventory, style);
        AESubGui.addBackButton(container, "back", widgets);
        setTextContent(TEXT_ID_DIALOG_TITLE, title != null ? title : GuiText.PortableCellPickupFilterTitle.text());

        this.pickupMode = new DynamicIconButton(
            () -> container.getPickupMode() == IncludeExclude.WHITELIST ? Icon.WHITELIST : Icon.BLACKLIST,
            GuiText.PortableCellPickupFilterWhitelist.text(),
            this::pickupModeTooltip,
            container::togglePickupMode);
        this.matchNbt = new SettingToggleButton<>(Settings.PORTABLE_CELL_PICKUP_MATCH_NBT, YesNo.YES,
            (button, backwards) -> container.toggleMatchNbt());
        this.matchDamage = new SettingToggleButton<>(Settings.PORTABLE_CELL_PICKUP_MATCH_DAMAGE, YesNo.YES,
            (button, backwards) -> container.toggleMatchDamage());
        this.matchOreDictionary = new SettingToggleButton<>(Settings.PORTABLE_CELL_PICKUP_MATCH_ORE_DICTIONARY,
            YesNo.NO, (button, backwards) -> container.toggleMatchOreDictionary());
        addToLeftToolbar(new ActionButton(ActionItems.PORTABLE_CELL_PICKUP_FILTER_CLEAR, container::clearPickupConfig));
        addToLeftToolbar(this.pickupMode);
        addToLeftToolbar(this.matchNbt);
        addToLeftToolbar(this.matchDamage);
        addToLeftToolbar(this.matchOreDictionary);
        updateButtons();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateButtons();
    }

    private void updateButtons() {
        this.matchNbt.set(container.isMatchNbt() ? YesNo.YES : YesNo.NO);
        this.matchDamage.set(container.isMatchDamage() ? YesNo.YES : YesNo.NO);
        this.matchOreDictionary.set(container.isMatchOreDictionary() ? YesNo.YES : YesNo.NO);
    }

    private List<ITextComponent> pickupModeTooltip() {
        return container.getPickupMode() == IncludeExclude.WHITELIST
            ? List.of(GuiText.PortableCellPickupFilterWhitelist.text())
            : List.of(GuiText.PortableCellPickupFilterBlacklist.text());
    }
}
