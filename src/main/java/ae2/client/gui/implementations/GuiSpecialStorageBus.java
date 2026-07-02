package ae2.client.gui.implementations;

import ae2.api.config.AccessRestriction;
import ae2.api.config.ActionItems;
import ae2.api.config.Settings;
import ae2.api.config.StorageFilter;
import ae2.api.config.YesNo;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.PaletteColor;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.implementations.ContainerStorageBus;
import ae2.core.localization.GuiText;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;

public class GuiSpecialStorageBus<T extends ContainerStorageBus> extends GuiUpgradeable<T> {
    private final SettingToggleButton<AccessRestriction> rwMode;
    private final SettingToggleButton<StorageFilter> storageFilter;
    private final SettingToggleButton<YesNo> filterOnExtract;

    public GuiSpecialStorageBus(T container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
        super(container, playerInventory, title, style);
        widgets.addOpenPriorityButton();
        addToLeftToolbar(new ActionButton(ActionItems.CLOSE, container::clear));
        this.rwMode = new ServerSettingToggleButton<>(Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.storageFilter = new ServerSettingToggleButton<>(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.filterOnExtract = new ServerSettingToggleButton<>(Settings.FILTER_ON_EXTRACT, YesNo.YES);
        this.addToLeftToolbar(this.storageFilter);
        this.addToLeftToolbar(this.filterOnExtract);
        this.addToLeftToolbar(this.rwMode);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.storageFilter.set(this.container.getStorageFilter());
        this.rwMode.set(this.container.getReadWriteMode());
        this.filterOnExtract.set(this.container.getFilterOnExtract());
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        GlStateManager.pushMatrix();
        GlStateManager.translate(10, 17, 0);
        GlStateManager.scale(0.6f, 0.6f, 1);
        int color = this.style != null
            ? this.style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB() & 0xFFFFFF
            : 0x404040;
        ITextComponent connectedTo = container.getConnectedTo();
        this.fontRenderer.drawString(getConnectedToLine(connectedTo), 0, 0, color);
        GlStateManager.popMatrix();
    }

    private String getConnectedToLine(ITextComponent connectedTo) {
        return connectedTo != null
            ? GuiText.AttachedTo.getLocal(connectedTo.getFormattedText())
            : GuiText.Unattached.getLocal();
    }
}
