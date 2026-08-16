package ae2.client.gui.me.items;

import ae2.api.client.PatternImportPriority;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.ITooltip;
import ae2.client.gui.widgets.TabButton;
import ae2.client.patternimport.PatternImportPriorityOrder;
import ae2.container.me.patternencode.ContainerPatternEncodingTerm;
import ae2.core.localization.GuiText;
import ae2.text.TextComponentItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jspecify.annotations.NonNull;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class GuiPatternImportPrioritySettings<C extends ContainerPatternEncodingTerm> extends AEBaseGui<C> {
    private static final int ROWS_PER_PAGE = 6;
    private static final int BUTTON_NAME_X = 44;
    private static final int BUTTON_Y = 28;
    private static final int BUTTON_SPACING = 24;
    private static final int NAME_WIDTH = 138;

    private final AEBaseGui<C> parent;
    private final List<PriorityOrderButton> priorityButtons = new ArrayList<>();
    private final AE2Button previousPageButton;
    private final AE2Button nextPageButton;
    private int currentPage;

    public GuiPatternImportPrioritySettings(AEBaseGui<C> parent) {
        super(parent.getContainer(), parent.getContainer().getPlayerInventory(),
            GuiStyleManager.loadStyleDoc("/screens/terminals/pattern_import_priority_settings.json"));
        this.parent = parent;

        widgets.add("back", new TabButton(Icon.BACK,
            TextComponentItemStack.of(parent.getContainer().getHost().getMainContainerIcon()), this::returnToParent));
        this.previousPageButton = widgets.addButton("previousPage", new TextComponentString("<"), this::previousPage);
        this.nextPageButton = widgets.addButton("nextPage", new TextComponentString(">"), this::nextPage);
        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            PriorityOrderButton button = new PriorityOrderButton(this::movePriorityToFront);
            widgets.add("priorityButton" + i, button);
            this.priorityButtons.add(button);
        }

        setTextContent(TEXT_ID_DIALOG_TITLE, GuiText.PatternImportPrioritiesTitle.text());
        updateButtons();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateButtons();
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        List<PatternImportPriority> priorities = PatternImportPriorityOrder.getOrderedPriorities();
        int startIndex = this.currentPage * ROWS_PER_PAGE;
        int visibleCount = Math.clamp(priorities.size() - startIndex, 0, ROWS_PER_PAGE);
        for (int row = 0; row < visibleCount; row++) {
            PatternImportPriority priority = priorities.get(startIndex + row);
            int y = BUTTON_Y + row * BUTTON_SPACING + 6;
            String name = this.fontRenderer.trimStringToWidth(priority.getDisplayName().getFormattedText(), NAME_WIDTH);
            this.fontRenderer.drawString(name, BUTTON_NAME_X, y, 0x404040);
        }
    }

    private void updateButtons() {
        List<PatternImportPriority> priorities = PatternImportPriorityOrder.getOrderedPriorities();
        int pageCount = getPageCount(priorities.size());
        if (this.currentPage >= pageCount) {
            this.currentPage = Math.max(0, pageCount - 1);
        }

        int startIndex = this.currentPage * ROWS_PER_PAGE;
        for (int row = 0; row < this.priorityButtons.size(); row++) {
            int priorityIndex = startIndex + row;
            PriorityOrderButton button = this.priorityButtons.get(row);
            if (priorityIndex < priorities.size()) {
                PatternImportPriority priority = priorities.get(priorityIndex);
                button.setPriorityIndex(priorityIndex);
                button.setMessage(new TextComponentString(Integer.toString(priorityIndex)));
                button.setTooltipLines(priority.getTooltipMessage());
                button.visible = true;
                button.enabled = true;
            } else {
                button.setPriorityIndex(-1);
                button.setTooltipLines(List.of());
                button.visible = false;
                button.enabled = false;
            }
        }

        this.previousPageButton.enabled = this.currentPage > 0;
        this.nextPageButton.enabled = this.currentPage + 1 < pageCount;
        setTextContent("page_info", new TextComponentString((this.currentPage + 1) + " / " + Math.max(1, pageCount)));
    }

    private void movePriorityToFront(int priorityIndex) {
        List<PatternImportPriority> priorities = PatternImportPriorityOrder.getOrderedPriorities();
        if (priorityIndex < 0 || priorityIndex >= priorities.size()) {
            return;
        }

        PatternImportPriorityOrder.moveToFront(priorities.get(priorityIndex).getId());
        this.currentPage = 0;
        updateButtons();
    }

    private void previousPage() {
        if (this.currentPage > 0) {
            this.currentPage--;
            updateButtons();
        }
    }

    private void nextPage() {
        int pageCount = getPageCount(PatternImportPriorityOrder.getOrderedPriorities().size());
        if (this.currentPage + 1 < pageCount) {
            this.currentPage++;
            updateButtons();
        }
    }

    private int getPageCount(int totalPriorities) {
        return Math.max(1, (totalPriorities + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private void returnToParent() {
        switchToScreen(this.parent);
        this.parent.returnFromSubScreen(this);
    }

    private static class PriorityOrderButton extends AE2Button implements ITooltip {
        private final IntConsumer onPressWithIndex;
        private int priorityIndex = -1;
        private List<ITextComponent> tooltipLines = List.of();

        private PriorityOrderButton(IntConsumer onPressWithIndex) {
            super(new TextComponentString(""), null);
            this.onPressWithIndex = onPressWithIndex;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            boolean releasedInside = this.enabled && this.visible
                && mouseX >= this.x
                && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
            super.mouseReleased(mouseX, mouseY);
            if (releasedInside && this.priorityIndex >= 0) {
                this.onPressWithIndex.accept(this.priorityIndex);
            }
        }

        private void setPriorityIndex(int priorityIndex) {
            this.priorityIndex = priorityIndex;
        }

        private void setTooltipLines(List<ITextComponent> tooltipLines) {
            this.tooltipLines = tooltipLines;
        }

        @Override
        public @NonNull List<ITextComponent> getTooltipMessage() {
            return this.tooltipLines;
        }

        @Override
        public Rectangle getTooltipArea() {
            return new Rectangle(this.x, this.y, this.width, this.height);
        }

        @Override
        public boolean isTooltipAreaVisible() {
            return this.visible;
        }
    }
}
