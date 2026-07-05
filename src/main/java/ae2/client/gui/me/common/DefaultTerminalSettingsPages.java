package ae2.client.gui.me.common;

import ae2.api.client.terminalsettings.TerminalSettingsPage;
import ae2.api.client.terminalsettings.TerminalSettingsPageContext;
import ae2.api.client.terminalsettings.TerminalSettingsPageProvider;
import ae2.client.gui.Icon;
import ae2.core.AppEng;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

final class DefaultTerminalSettingsPages {
    static final ResourceLocation GENERAL_ID = AppEng.makeId("general_terminal_settings");
    static final ResourceLocation SEARCH_ID = AppEng.makeId("search_mode_settings");
    static final ResourceLocation WIRELESS_ID = AppEng.makeId("wireless_terminal_settings");

    private static final TerminalSettingsPage EMPTY_PAGE = new TerminalSettingsPage() {
    };

    private DefaultTerminalSettingsPages() {
    }

    static List<TerminalSettingsPageProvider> getDefaults() {
        List<TerminalSettingsPageProvider> pages = new ObjectArrayList<>();
        pages.add(new BuiltinPageProvider(
            GENERAL_ID,
            Icon.COG,
            GuiText.TerminalSettingsTitle.text(),
            context -> !context.isWirelessOnlySettings() && context.hasGeneralTerminalSettingsPage()));
        pages.add(new BuiltinPageProvider(
            SEARCH_ID,
            Icon.TERMINAL_SEARCH_SETTINGS,
            GuiText.SearchSettingsTitle.text(),
            context -> !context.isWirelessOnlySettings()
                && context.hasGeneralSetting(GuiTerminalSettings.GeneralSetting.SEARCH)));
        pages.add(new BuiltinPageProvider(
            WIRELESS_ID,
            Icon.WIRELESS_SETTINGS_PAGE,
            GuiText.WirelessTerminalSettingsTitle.text(),
            TerminalSettingsPageHostContext::hasWirelessHost));
        return pages;
    }

    private record BuiltinPageProvider(ResourceLocation id, Icon icon, ITextComponent title,
                                       Visibility visibility) implements TerminalSettingsPageProvider {
        @Override
        public ITextComponent title(TerminalSettingsPageContext context) {
            return this.title;
        }

        @Override
        public boolean isVisible(TerminalSettingsPageContext context) {
            if (!(context instanceof TerminalSettingsPageHostContext hostContext)) {
                return false;
            }
            return this.visibility.isVisible(hostContext);
        }

        @Override
        public TerminalSettingsPage create(TerminalSettingsPageContext context) {
            return EMPTY_PAGE;
        }
    }

    @FunctionalInterface
    private interface Visibility {
        boolean isVisible(TerminalSettingsPageHostContext context);
    }
}
