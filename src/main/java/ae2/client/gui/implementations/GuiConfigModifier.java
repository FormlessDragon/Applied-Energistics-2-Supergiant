package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.container.implementations.ContainerConfigModifier;
import ae2.core.localization.GuiText;
import ae2.items.tools.ConfigModifierItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

import java.util.regex.Pattern;

public class GuiConfigModifier extends AEBaseGui<ContainerConfigModifier> {
    private static final Pattern NUMBER = Pattern.compile("[0-9]*");
    private final AE2Button modeButton;

    public GuiConfigModifier(ContainerConfigModifier container, InventoryPlayer playerInventory, GuiStyle style) {
        super(container, playerInventory, style);
        var dataInput = widgets.addTextField("data_input");
        dataInput.setMaxStringLength(15);
        dataInput.setText(String.valueOf(container.getData()));
        dataInput.setResponder(this::syncData);
        this.modeButton = widgets.addButton("mode", GuiText.ConfigModifierChangeMode.text(),
            () -> this.container.setMode(this.container.getMode().next()));
    }

    private static ITextComponent getModeText(ConfigModifierItem.Mode mode) {
        return switch (mode) {
            case ADD -> GuiText.ConfigModifierModeAdd.text();
            case SUB -> GuiText.ConfigModifierModeSub.text();
            case MUL -> GuiText.ConfigModifierModeMul.text();
            case DIV -> GuiText.ConfigModifierModeDiv.text();
            case MAX -> GuiText.ConfigModifierModeMax.text();
            case MIN -> GuiText.ConfigModifierModeMin.text();
            case SET -> GuiText.ConfigModifierModeSet.text();
            case RMV -> GuiText.ConfigModifierModeRmv.text();
        };
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.modeButton.setMessage(getModeText(this.container.getMode()));
    }

    private void syncData(String value) {
        if (!NUMBER.matcher(value).matches()) {
            return;
        }
        if (value.isEmpty()) {
            return;
        }
        try {
            this.container.setData(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
        }
    }
}
