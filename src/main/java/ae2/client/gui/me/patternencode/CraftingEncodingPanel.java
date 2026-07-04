package ae2.client.gui.me.patternencode;

import ae2.api.config.ActionItems;
import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.WidgetContainer;
import ae2.client.gui.style.Blitter;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.ToggleButton;
import ae2.container.SlotSemantics;
import ae2.container.me.patternencode.ContainerPatternEncodingTerm;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import net.minecraft.client.gui.Gui;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;

import java.awt.Rectangle;
import java.util.List;

public class CraftingEncodingPanel extends EncodingModePanel {
    private static final Blitter BG = Blitter.texture("guis/pattern_modes.png").src(0, 0, 124, 66);

    private final ActionButton clearBtn;
    private final ToggleButton substitutionsBtn;
    private final ToggleButton fluidSubstitutionsBtn;

    public CraftingEncodingPanel(AEBaseGui<? extends ContainerPatternEncodingTerm> screen, WidgetContainer widgets) {
        super(screen, widgets);
        this.clearBtn = new ActionButton(ActionItems.S_CLOSE, this.container::clear);
        this.clearBtn.setHalfSize(true);
        this.clearBtn.setDisableBackground(true);
        widgets.add("craftingClearPattern", this.clearBtn);

        this.substitutionsBtn = new ToggleButton(Icon.S_SUBSTITUTION_ENABLED, Icon.S_SUBSTITUTION_DISABLED,
            this.container::setSubstitute);
        this.substitutionsBtn.setHalfSize(true);
        this.substitutionsBtn.setDisableBackground(true);
        this.substitutionsBtn.setTooltipOn(List.of(
            Tooltips.SubstitutionsOn.text(),
            Tooltips.SubstitutionsDescEnabled.text()));
        this.substitutionsBtn.setTooltipOff(List.of(
            Tooltips.SubstitutionsOff.text(),
            Tooltips.SubstitutionsDescDisabled.text()));
        widgets.add("craftingSubstitutions", this.substitutionsBtn);

        this.fluidSubstitutionsBtn = new ToggleButton(Icon.S_FLUID_SUBSTITUTION_ENABLED,
            Icon.S_FLUID_SUBSTITUTION_DISABLED, this.container::setSubstituteFluids);
        this.fluidSubstitutionsBtn.setHalfSize(true);
        this.fluidSubstitutionsBtn.setDisableBackground(true);
        this.fluidSubstitutionsBtn.setTooltipOn(List.of(
            Tooltips.FluidSubstitutions.text(),
            Tooltips.FluidSubstitutionsDescEnabled.text()));
        this.fluidSubstitutionsBtn.setTooltipOff(List.of(
            Tooltips.FluidSubstitutions.text(),
            Tooltips.FluidSubstitutionsDescDisabled.text()));
        widgets.add("craftingFluidSubstitutions", this.fluidSubstitutionsBtn);
    }

    @Override
    Icon getIcon() {
        return Icon.TAB_CRAFTING;
    }

    @Override
    public ITextComponent getTabTooltip() {
        return GuiText.CraftingPattern.text();
    }

    @Override
    public void drawBackgroundLayer(Rectangle bounds, Point mouse) {
        BG.dest(bounds.x + this.position.x() - 1, bounds.y + this.position.y() + 1).blit();

        if (this.container.substituteFluids && mouse.isIn(this.fluidSubstitutionsBtn.getTooltipArea())) {
            for (int slotIndex : this.container.slotsSupportingFluidSubstitution) {
                drawSlotGreenBG(bounds, this.container.getCraftingGridSlots()[slotIndex]);
            }
        }
    }

    private void drawSlotGreenBG(Rectangle bounds, Slot slot) {
        int x = bounds.x + slot.xPos;
        int y = bounds.y + slot.yPos;
        Gui.drawRect(x, y, x + 16, y + 16, 0xFF7AC25F);
    }

    @Override
    public void updateBeforeRender() {
        this.substitutionsBtn.setState(this.container.substitute);
        this.fluidSubstitutionsBtn.setState(this.container.substituteFluids);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        this.clearBtn.setVisibility(visible);
        this.substitutionsBtn.setVisibility(visible);
        this.fluidSubstitutionsBtn.setVisibility(visible);
        this.screen.setSlotsHidden(SlotSemantics.CRAFTING_GRID, !visible);
        this.screen.setSlotsHidden(SlotSemantics.CRAFTING_RESULT, !visible);
    }
}
