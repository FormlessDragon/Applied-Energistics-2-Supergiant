package ae2.container.implementations;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.stacks.AEItemKey;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.interfaces.IProgressProvider;
import ae2.container.slot.AppEngSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.helpers.patternprovider.PatternProviderCapacity;
import ae2.tile.crafting.TileMolecularAssembler;
import ae2.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.List;

public class ContainerMolecularAssembler extends AEBaseContainer
    implements IProgressProvider, IConfigurableObject, PatternModifierPanel.Host {
    public static final String ACTION_SET_PAGE = "setPage";
    private static final int MAX_CRAFT_PROGRESS = 100;

    private final TileMolecularAssembler host;
    private final List<AppEngSlot> patternSlots = new ObjectArrayList<>();
    private final PatternModifierPanel patternModifierPanel;

    @GuiSync(4)
    public int craftProgress;
    @GuiSync(5)
    public YesNo showInAccessTerminal = YesNo.YES;
    @GuiSync(6)
    public int activePatternSlots = 9;
    @GuiSync(7)
    public int currentPage;
    @GuiSync(8)
    public int pageCount = 1;
    @GuiSync(9)
    public boolean patternModifierPanelAvailable;

    public ContainerMolecularAssembler(InventoryPlayer playerInventory, TileMolecularAssembler host) {
        super(playerInventory, host);
        this.host = host;
        this.registerClientAction(ACTION_SET_PAGE, Integer.class, this::setPage);

        AppEngInternalInventory patternInventory = host.getPatternInventory();
        for (int index = 0; index < patternInventory.size(); index++) {
            AppEngSlot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.MOLECULAR_ASSEMBLER_PATTERN,
                patternInventory, index);
            this.patternSlots.add(slot);
            this.addSlot(slot, SlotSemantics.ENCODED_PATTERN);
        }

        this.setupUpgrades(host.getUpgrades());
        this.addPlayerInventorySlots(8, 163);
        this.patternModifierPanel = new PatternModifierPanel(this);
        this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();
        this.updatePatternSlotState();
    }

    public TileMolecularAssembler getHost() {
        return this.host;
    }

    private static int clampPage(int page, int pageCount) {
        if (pageCount <= 0) {
            return 0;
        }
        return Math.clamp(page, 0, pageCount - 1);
    }

    @Override
    public void onClientDataSync(ShortSet updatedFields) {
        super.onClientDataSync(updatedFields);
        this.updatePatternSlotState();
    }

    @Override
    public boolean isValidForSlot(Slot slot, ItemStack stack) {
        if (!(slot instanceof AppEngSlot appEngSlot && this.patternSlots.contains(appEngSlot))) {
            return true;
        }
        return isPatternSlotVisible(appEngSlot)
            && isAssemblerPattern(stack)
            && !hasSamePatternInOtherSlot(appEngSlot, stack);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (!isClientSide() && index >= 0 && index < this.inventorySlots.size()) {
            Slot sourceSlot = this.inventorySlots.get(index);
            if (sourceSlot != null && isPlayerSideSlot(sourceSlot) && isAssemblerPattern(sourceSlot.getStack())) {
                return moveSinglePatternFromPlayerSlot(sourceSlot, player);
            }
        }
        return super.transferStackInSlot(player, index);
    }

    @Override
    protected boolean isValidQuickMoveDestination(Slot candidateSlot, ItemStack stackToMove, boolean fromPlayerSide) {
        if (fromPlayerSide && candidateSlot instanceof AppEngSlot appEngSlot && this.patternSlots.contains(appEngSlot)
            && hasSamePattern(stackToMove)) {
            return false;
        }
        return super.isValidQuickMoveDestination(candidateSlot, stackToMove, fromPlayerSide);
    }

    @Override
    public int getCurrentProgress() {
        return this.craftProgress;
    }

    @Override
    public int getMaxProgress() {
        return MAX_CRAFT_PROGRESS;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.host.getConfigManager();
    }

    public YesNo getShowInAccessTerminal() {
        return this.showInAccessTerminal;
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public int getPageCount() {
        return this.pageCount;
    }

    public boolean isPatternModifierPanelAvailable() {
        return this.patternModifierPanelAvailable;
    }

    public PatternModifierPanel getPatternModifierPanel() {
        return this.patternModifierPanel;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.craftProgress = this.host.getCraftingProgress();
            this.showInAccessTerminal = this.host.getConfigManager().getSetting(Settings.PATTERN_ACCESS_TERMINAL);
            this.activePatternSlots = this.host.getActivePatternSlots();
            this.pageCount = this.host.getPatternPageCount();
            this.currentPage = clampPage(this.currentPage, this.pageCount);
            this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();
        }

        this.updatePatternSlotState();
        super.broadcastChanges();
    }

    public void setPage(int page) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PAGE, page);
            return;
        }
        this.currentPage = clampPage(page, this.host.getPatternPageCount());
        this.updatePatternSlotState();
        this.detectAndSendChanges();
    }

    public void updatePatternModifierPanelVisibleSlots(boolean visible) {
        this.patternModifierPanel.updateSlotState(visible && this.patternModifierPanelAvailable);
    }

    @Override
    public void registerPatternModifierPanelAction(String action, Runnable runnable) {
        registerClientAction(action, runnable);
    }

    @Override
    public void sendPatternModifierPanelAction(String action) {
        sendClientAction(action);
    }

    @Override
    public void lockPatternModifierPlayerInventorySlot(int slot) {
        lockPlayerInventorySlot(slot);
    }

    private void updatePatternSlotState() {
        for (AppEngSlot slot : this.patternSlots) {
            boolean enabled = isPatternSlotVisible(slot);
            slot.setSlotEnabled(enabled);
            slot.setActive(enabled);
        }
        this.patternModifierPanel.updateSlotState(this.patternModifierPanelAvailable);
    }

    private boolean isPatternSlotVisible(AppEngSlot slot) {
        return slot.getSlotIndex() < this.activePatternSlots
            && PatternProviderCapacity.isPatternSlotOnPage(slot.getSlotIndex(), this.currentPage);
    }

    private boolean hasSamePattern(ItemStack stack) {
        AEItemKey pattern = AEItemKey.of(stack);
        return pattern != null && this.host.containsPattern(pattern);
    }

    private ItemStack moveSinglePatternFromPlayerSlot(Slot sourceSlot, EntityPlayer player) {
        if (!sourceSlot.canTakeStack(player) || sourceSlot.getStack().isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getStack();
        ItemStack originalStack = sourceStack.copy();
        for (AppEngSlot targetSlot : this.patternSlots) {
            if (targetSlot.getHasStack() || !targetSlot.isItemValid(sourceStack)) {
                continue;
            }

            ItemStack inserted = sourceStack.copy();
            inserted.setCount(1);
            targetSlot.putStack(inserted);
            targetSlot.onSlotChanged();
            sourceSlot.decrStackSize(1);
            sourceSlot.onSlotChanged();
            detectAndSendChanges();
            return originalStack;
        }

        return ItemStack.EMPTY;
    }

    private boolean isAssemblerPattern(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, getPlayer().world);
        return details instanceof IAssemblerPattern;
    }

    private boolean hasSamePatternInOtherSlot(AppEngSlot targetSlot, ItemStack stack) {
        AEItemKey pattern = AEItemKey.of(stack);
        if (pattern == null) {
            return false;
        }

        for (AppEngSlot otherSlot : this.patternSlots) {
            if (otherSlot == targetSlot) {
                continue;
            }

            AEItemKey otherPattern = AEItemKey.of(otherSlot.getStack());
            if (pattern.equals(otherPattern)) {
                return true;
            }
        }

        return false;
    }
}
