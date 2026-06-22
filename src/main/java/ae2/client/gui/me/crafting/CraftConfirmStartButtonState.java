package ae2.client.gui.me.crafting;

import ae2.core.localization.GuiText;

record CraftConfirmStartButtonState(GuiText label, boolean clickable, boolean highlighted) {

    static CraftConfirmStartButtonState compute(boolean hasNoCpu, boolean planIsStartable, boolean hasMissingEntries,
                                                boolean shiftDown, boolean mergeAvailable, boolean ctrlDown) {
        var label = hasMissingEntries ? GuiText.ForceStart : mergeAvailable && !ctrlDown ? GuiText.Merge : GuiText.Start;
        var canUseCpu = !hasNoCpu || (mergeAvailable && !ctrlDown);
        var clickable = planIsStartable && canUseCpu && (!hasMissingEntries || shiftDown);
        var highlighted = planIsStartable && ((hasMissingEntries && shiftDown) || (mergeAvailable && !ctrlDown));
        return new CraftConfirmStartButtonState(label, clickable, highlighted);
    }
}
