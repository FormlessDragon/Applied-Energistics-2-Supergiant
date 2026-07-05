package ae2.client.gui.me.common;

interface TerminalSettingsPageHostContext {
    boolean isWirelessOnlySettings();

    boolean hasWirelessHost();

    boolean hasGeneralTerminalSettingsPage();

    boolean hasGeneralSetting(GuiTerminalSettings.GeneralSetting setting);
}
