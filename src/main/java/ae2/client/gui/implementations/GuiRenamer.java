package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.container.implementations.ContainerRenamer;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.io.IOException;
import java.util.Collection;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiRenamer extends AEBaseGui<ContainerRenamer> implements ITextFieldGui {

    private final AETextField name;
    private boolean nameInitialized;
    private boolean submitted;

    public GuiRenamer(ContainerRenamer container, InventoryPlayer playerInventory, ITextComponent title,
                      GuiStyle style) {
        super(container, playerInventory, style);
        this.name = widgets.addTextField("name");
        this.name.setMaxStringLength(32);
        this.name.setResponder(container::setName);
    }

    @Override
    public void initGui() {
        super.initGui();
        setInitialFocus(this.name);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        initializeName();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == 28 || keyCode == 156
            || this.mc.gameSettings.keyBindInventory.isActiveAndMatches(keyCode)) {
            submitName();
            this.mc.player.closeScreen();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
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

    private void initializeName() {
        if (!this.nameInitialized && container.isInitialNameReady()) {
            this.name.setText(container.getInitialName());
            this.nameInitialized = true;
        }
    }

    private void submitName() {
        if (this.submitted || !this.nameInitialized) {
            return;
        }
        this.submitted = true;
        container.setName(this.name.getText());
    }

    @Override
    public Collection<? extends GuiTextField> getTextFields() {
        return ObjectLists.singleton(this.name);
    }
}
