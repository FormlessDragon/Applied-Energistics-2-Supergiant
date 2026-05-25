package appeng.client.gui.me.crafting;

import appeng.core.localization.GuiText;

record CraftConfirmStartButtonState(GuiText label, boolean clickable, boolean highlighted) {

    static CraftConfirmStartButtonState compute(boolean hasNoCpu, boolean planIsStartable, boolean hasMissingEntries,
                                                boolean shiftDown) {
        var label = hasMissingEntries ? GuiText.ForceStart : GuiText.Start;
        var clickable = !hasNoCpu && planIsStartable && (!hasMissingEntries || shiftDown);
        var highlighted = planIsStartable && hasMissingEntries && shiftDown;
        return new CraftConfirmStartButtonState(label, clickable, highlighted);
    }
}
