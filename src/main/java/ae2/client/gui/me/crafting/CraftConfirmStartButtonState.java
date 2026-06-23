package ae2.client.gui.me.crafting;

import ae2.core.localization.GuiText;

record CraftConfirmStartButtonState(GuiText label, boolean clickable, boolean highlighted) {

    static CraftConfirmStartButtonState compute(boolean hasNoCpu, boolean planIsStartable, boolean hasMissingEntries,
                                                boolean shiftDown, boolean mergeAvailable, boolean ctrlDown) {
        var label = getLabel(hasMissingEntries, mergeAvailable, ctrlDown);
        var canUseCpu = !hasNoCpu || (mergeAvailable && !ctrlDown);
        var clickable = planIsStartable && canUseCpu && (!hasMissingEntries || shiftDown);
        var highlighted = planIsStartable && ((hasMissingEntries && shiftDown) || (mergeAvailable && !ctrlDown));
        return new CraftConfirmStartButtonState(label, clickable, highlighted);
    }

    private static GuiText getLabel(boolean hasMissingEntries, boolean mergeAvailable, boolean ctrlDown) {
        if (hasMissingEntries) {
            return mergeAvailable && !ctrlDown ? GuiText.ForceMerge : GuiText.ForceStart;
        }
        return mergeAvailable && !ctrlDown ? GuiText.Merge : GuiText.Start;
    }
}
