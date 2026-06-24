package ae2.client.gui.me.items;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.client.gui.widgets.TabButton;
import ae2.container.me.items.ContainerPatternEncodingTerm;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import java.io.IOException;
import java.util.Collection;

public class GuiPatternItemRenamer extends AEBaseGui<ContainerPatternEncodingTerm> implements ITextFieldGui {

    private final GuiPatternEncodingTerm parent;
    private final int slotNumber;
    private final AETextField name;
    private boolean submitted;

    public GuiPatternItemRenamer(GuiPatternEncodingTerm parent, Slot slot, ITextComponent parentIconTooltip) {
        super(parent.getContainer(), parent.getContainer().getPlayerInventory(),
            GuiStyleManager.loadStyleDoc("/screens/renamer.json"));
        this.parent = parent;
        this.slotNumber = slot.slotNumber;
        this.name = widgets.addTextField("name");
        this.name.setMaxStringLength(32);
        this.name.setResponder(text -> container.renameProcessingPatternItem(this.slotNumber, text));
        widgets.add("back", new TabButton(Icon.BACK, parentIconTooltip, this::returnToParent));

        ItemStack stack = slot.getStack();
        this.name.setText(stack.hasDisplayName() ? stack.getDisplayName() : "");
    }

    @Override
    public void initGui() {
        super.initGui();
        setInitialFocus(this.name);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == 28 || keyCode == 156
            || this.mc.gameSettings.keyBindInventory.isActiveAndMatches(keyCode)) {
            submitName();
            returnToParent();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (handleSelectionPopupMouseClicked(mouseX, mouseY)) {
            return;
        }

        if (mouseButton == 1 && this.name.isMouseOver(mouseX, mouseY)) {
            this.name.setTextFromClient("");
            this.name.setFocused(true);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onGuiClosed() {
        submitName();
        super.onGuiClosed();
    }

    private void submitName() {
        if (this.submitted) {
            return;
        }
        this.submitted = true;
        container.renameProcessingPatternItem(this.slotNumber, this.name.getText());
    }

    private void returnToParent() {
        switchToScreen(this.parent);
        this.parent.returnFromSubScreen(this);
    }

    @Override
    public Collection<? extends GuiTextField> getTextFields() {
        return ObjectLists.singleton(this.name);
    }
}
