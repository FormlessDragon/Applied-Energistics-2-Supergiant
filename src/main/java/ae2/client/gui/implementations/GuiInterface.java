package ae2.client.gui.implementations;

import ae2.api.config.FuzzyMode;
import ae2.api.config.Settings;
import ae2.client.gui.Icon;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.IconButton;
import ae2.client.gui.widgets.ServerSettingToggleButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerInterface;
import ae2.container.slot.AppEngSlot;
import ae2.core.definitions.AEItems;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

public class GuiInterface extends GuiUpgradeable<ContainerInterface> {

    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final List<SetAmountButton> amountButtons = new ObjectArrayList<>();
    private final PageButton previousPageButton;
    private final PageButton nextPageButton;

    public GuiInterface(ContainerInterface container, InventoryPlayer playerInventory, ITextComponent title, GuiStyle style) {
        super(container, playerInventory, title, style);

        this.fuzzyMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL));
        widgets.addOpenPriorityButton();
        this.previousPageButton = new PageButton(Icon.ARROW_LEFT, () -> container.setPage(container.getCurrentPage() - 1));
        this.nextPageButton = new PageButton(Icon.ARROW_RIGHT, () -> container.setPage(container.getCurrentPage() + 1));
        widgets.add("previousPage", this.previousPageButton);
        widgets.add("nextPage", this.nextPageButton);

        var configSlots = container.getSlots(SlotSemantics.CONFIG);
        for (int i = 0; i < configSlots.size(); i++) {
            int slotIndex = i;
            var button = new SetAmountButton(() -> {
                var configSlot = (AppEngSlot) container.getSlots(SlotSemantics.CONFIG).get(slotIndex);
                container.openSetAmountGui(configSlot.getSlotIndex());
            });
            button.setDisableBackground(true);
            button.setMessage(ButtonToolTips.InterfaceSetStockAmount.text());
            widgets.add("amtButton" + (i + 1), button);
            amountButtons.add(button);
        }
    }

    private static void repositionRowPair(List<Slot> slots, int firstRowY, int secondRowY) {
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int row = i / 9;
            int col = i % 9;
            slot.xPos = 8 + col * 18;
            slot.yPos = (row == 0 ? firstRowY : secondRowY);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.repositionInterfaceSlots();

        this.fuzzyMode.set(container.getFuzzyMode());
        this.fuzzyMode.setVisibility(container.hasUpgrade(AEItems.FUZZY_CARD.item()));
        setTextContent("interface_config",
            GuiText.InterfaceConfigPage.text(new TextComponentString(Integer.toString(container.getCurrentPage() + 1))));
        this.previousPageButton.setVisibility(container.getPageCount() > 1 && container.getCurrentPage() > 0);
        this.nextPageButton.setVisibility(container.getPageCount() > 1
            && container.getCurrentPage() + 1 < container.getPageCount());

        var configSlots = container.getSlots(SlotSemantics.CONFIG);
        for (int i = 0; i < amountButtons.size(); i++) {
            var button = amountButtons.get(i);
            button.setVisibility(!configSlots.get(i).getStack().isEmpty());
        }
    }

    private void repositionInterfaceSlots() {
        repositionRowPair(this.container.getSlots(SlotSemantics.CONFIG), 53, 113);
        repositionRowPair(this.container.getSlots(SlotSemantics.STORAGE), 71, 131);
    }

    static class SetAmountButton extends IconButton {
        SetAmountButton(Runnable onPress) {
            super(onPress);
        }

        @Override
        protected Icon getIcon() {
            return this.hovered ? Icon.COG : Icon.COG_DISABLED;
        }
    }

    static class PageButton extends IconButton {
        private final Icon icon;

        PageButton(Icon icon, Runnable onPress) {
            super(onPress);
            this.icon = icon;
            this.setMessage((icon == Icon.ARROW_LEFT ? GuiText.InterfacePagePrevious : GuiText.InterfacePageNext).text());
        }

        @Override
        protected Icon getIcon() {
            return this.icon;
        }
    }
}
