package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.container.implementations.ContainerProviderSelect;
import ae2.core.worlddata.PatternProviderMappingData.ProviderReference;
import ae2.core.localization.GuiText;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class GuiProviderSelect extends AEBaseGui<ContainerProviderSelect> implements ITextFieldGui {

    private static final int PAGE_SIZE = 6;
    private static final TextComponentString EMPTY_MESSAGE = new TextComponentString("");

    private final AETextField searchField;
    private final AE2Button[] entryButtons = new AE2Button[PAGE_SIZE];
    private final AE2Button mappingButton;
    private final AE2Button prevButton;
    private final AE2Button nextButton;
    private final AE2Button cancelButton;
    private String searchText = "";
    private boolean mappingMode;
    private int initialStateVersion = Integer.MIN_VALUE;
    private int page = 0;

    public GuiProviderSelect(ContainerProviderSelect container, InventoryPlayer playerInventory, GuiStyle style) {
        super(container, playerInventory, style);

        AESubGui.addBackButton(container, "back", this.widgets);

        this.searchField = this.widgets.addTextField("search");
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.searchField.setTooltipMessage(Collections.singletonList(GuiText.SearchTooltip.text()));

        for (int i = 0; i < PAGE_SIZE; i++) {
            int entryIndex = i;
            AE2Button button = new AE2Button(EMPTY_MESSAGE, () -> handleEntryLeftClick(entryIndex));
            this.widgets.add("entry" + i, button);
            this.entryButtons[i] = button;
        }
        this.mappingButton = this.widgets.addButton("mapping",
            new TextComponentTranslation("gui.ae2.ProviderSelectMapping"), this::toggleMappingMode);
        this.prevButton = this.widgets.addButton("prev", new TextComponentString("<"), () -> changePage(-1));
        this.nextButton = this.widgets.addButton("next", new TextComponentString(">"), () -> changePage(1));
        this.cancelButton = this.widgets.addButton("cancel", GuiText.Cancel.text(), AESubGui::goBack);

        this.syncInitialState();
        this.refreshButtons();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 1) {
            if (this.searchField.isMouseOver(mouseX, mouseY)) {
                this.searchField.setText("");
                this.searchText = "";
                this.container.setSearchText("");
                this.page = 0;
                refreshButtons();
                return;
            }

            if (this.mappingMode) {
                for (int i = 0; i < this.entryButtons.length; i++) {
                    if (isMouseOverButton(this.entryButtons[i], mouseX, mouseY)) {
                        this.container.unbindProviderMapping(getProviderReference(i));
                        return;
                    }
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateBeforeRender() {
        super.updateBeforeRender();
        this.searchField.setVisible(true);
        if (this.container.consumeProviderEntriesChanged()
                || syncInitialState()
                || syncMappingMode()
                || updateSearch()
        ) {
            refreshButtons();
        }
    }

    @Override
    public Collection<? extends GuiTextField> getTextFields() {
        return Collections.singleton(this.searchField);
    }

    private void refreshButtons() {
        int start = this.page * PAGE_SIZE;
        int visibleEntryCount = this.container.getVisibleEntryCount();
        if (visibleEntryCount == 0) {
            this.page = 0;
            return;
        }

        int maxPage = (visibleEntryCount - 1) / PAGE_SIZE;
        if (this.page > maxPage) {
            this.page = maxPage;
        }

        for (int i = 0; i < PAGE_SIZE; i++) {
            AE2Button button = this.entryButtons[i];
            int visibleIndex = start + i;
            boolean hasEntry = visibleIndex < visibleEntryCount;
            button.visible = hasEntry;
            button.enabled = hasEntry;
            if (hasEntry) {
                String label = this.container.getVisibleEntryLabel(visibleIndex);
                button.setMessage(new TextComponentString(label));
            } else {
                button.setMessage(EMPTY_MESSAGE);
            }
        }

        this.mappingButton.visible = true;
        this.mappingButton.enabled = true;
        this.mappingButton.setForceHighlighted(this.mappingMode);
        this.prevButton.visible = true;
        this.prevButton.enabled = this.page > 0;
        this.nextButton.visible = true;
        this.nextButton.enabled = (this.page + 1) * PAGE_SIZE < visibleEntryCount;
        this.cancelButton.visible = true;
        this.cancelButton.enabled = true;
    }

    private void changePage(int delta) {
        int newPage = this.page + delta;
        if (newPage < 0) {
            return;
        }
        if (newPage * PAGE_SIZE >= this.container.getVisibleEntryCount()) {
            return;
        }
        this.page = newPage;
        refreshButtons();
    }

    private void toggleMappingMode() {
        this.mappingMode = !this.mappingMode;
        this.container.setMappingMode(this.mappingMode);
        this.container.setSearchText("");
        this.page = 0;
        refreshButtons();
    }

    private void handleEntryLeftClick(int entryIndex) {
        if (this.mappingMode) {
            ProviderReference reference = getProviderReference(entryIndex);
            String mappingKey = getSearchFieldText();
            this.container.bindProviderMapping(reference, mappingKey);
            if (isShiftKeyDown()) {
                this.container.uploadToProvider(getProviderId(entryIndex));
            }
            return;
        }

        this.container.uploadToProvider(getProviderId(entryIndex));
    }

    private long getProviderId(int entryIndex) {
        return this.container.getVisibleEntryId(getVisibleEntryIndex(entryIndex));
    }

    private ProviderReference getProviderReference(int entryIndex) {
        return this.container.getVisibleEntryReference(getVisibleEntryIndex(entryIndex));
    }

    private int getVisibleEntryIndex(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= PAGE_SIZE) {
            throw new IllegalArgumentException("Provider entry button index out of range: " + entryIndex);
        }

        int visibleIndex = this.page * PAGE_SIZE + entryIndex;
        if (visibleIndex >= this.container.getVisibleEntryCount()) {
            throw new IllegalArgumentException("Provider entry button has no visible entry: " + entryIndex
                + " (visible index: " + visibleIndex + ")");
        }

        return visibleIndex;
    }

    private boolean syncInitialState() {
        int version = this.container.getInitialStateVersion();
        if (this.initialStateVersion == version) {
            return false;
        }

        this.initialStateVersion = version;
        this.searchText = this.container.getInitialSearchText();
        this.searchField.setText(this.searchText);
        this.container.setSearchText(this.searchText);
        this.mappingMode = this.container.isMappingMode();
        this.page = 0;
        return true;
    }

    private boolean syncMappingMode() {
        boolean currentMappingMode = this.container.isMappingMode();
        if (this.mappingMode == currentMappingMode) {
            return false;
        }

        this.mappingMode = currentMappingMode;
        this.page = 0;
        return true;
    }

    private boolean updateSearch() {
        String text = getSearchFieldText();
        if (this.searchText.equals(text)) {
            return false;
        }

        this.searchText = text;
        if (!this.mappingMode) {
            this.container.setSearchText(text);
        }
        this.page = 0;
        return true;
    }

    private String getSearchFieldText() {
        String text = this.searchField.getText();
        if (text == null) {
            text = "";
        }
        return text;
    }

    private static boolean isMouseOverButton(AE2Button button, int mouseX, int mouseY) {
        return button.visible && button.enabled
            && mouseX >= button.x
            && mouseY >= button.y
            && mouseX < button.x + button.width
            && mouseY < button.y + button.height;
    }
}
