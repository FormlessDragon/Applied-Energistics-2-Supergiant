package ae2.client.gui.me.crafting;

import ae2.container.me.crafting.CraftingPlanSummary;
import ae2.container.me.crafting.CraftingPlanSummaryEntry;
import ae2.core.localization.GuiText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftConfirmStartButtonStateTest {
    private static CraftingPlanSummary missingPlan() {
        // Only the counts are used to decide whether the plan needs forced start.
        var missing = new CraftingPlanSummaryEntry(null, 3, 0, 0, 0, 0, 0, false, false);
        return new CraftingPlanSummary(46, true, List.of(missing));
    }

    private static CraftConfirmStartButtonState state(CraftingPlanSummary plan, boolean noCpu,
                                                       boolean shift, boolean merge, boolean ctrl) {
        return CraftConfirmStartButtonState.compute(noCpu, CraftConfirmStartButtonState.canSelectCpu(plan),
            plan != null && plan.hasMissingEntries(), shift, merge, ctrl);
    }

    @Test
    void missingPreviewAllowsCpuSelectionAndRequiresShiftToStart() {
        var plan = missingPlan();
        assertTrue(CraftConfirmStartButtonState.canSelectCpu(plan));
        var ordinary = state(plan, false, false, false, false);
        assertEquals(GuiText.ForceStart, ordinary.label());
        assertFalse(ordinary.clickable());
        assertFalse(ordinary.highlighted());
        var forced = state(plan, false, true, false, false);
        assertEquals(GuiText.ForceStart, forced.label());
        assertTrue(forced.clickable());
        assertTrue(forced.highlighted());
    }

    @Test
    void shiftStillRequiresAnAvailableCpuOrMergeTarget() {
        assertFalse(state(missingPlan(), true, true, false, false).clickable());
        var forcedMerge = state(missingPlan(), true, true, true, false);
        assertEquals(GuiText.ForceMerge, forcedMerge.label());
        assertTrue(forcedMerge.clickable());
        assertFalse(state(missingPlan(), true, true, true, true).clickable());
        assertTrue(state(missingPlan(), false, true, true, true).clickable());
    }

    @Test
    void aPureSimulationOrPendingCalculationCannotBeForced() {
        var preview = new CraftingPlanSummary(46, true, List.of());
        assertFalse(CraftConfirmStartButtonState.canSelectCpu(preview));
        assertFalse(state(preview, false, true, true, false).clickable());
        assertFalse(CraftConfirmStartButtonState.canSelectCpu(null));
        assertFalse(state(null, false, true, true, false).clickable());
    }

    @Test
    void aCompletePlanStartsWithoutShift() {
        var plan = new CraftingPlanSummary(46, false, List.of());
        var ordinary = state(plan, false, false, false, false);
        assertTrue(ordinary.clickable());
        assertFalse(ordinary.highlighted());
        assertEquals(GuiText.Start, ordinary.label());
    }
}
