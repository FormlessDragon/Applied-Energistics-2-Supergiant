package ae2.container.implementations;

import ae2.api.config.BlockingMode;
import ae2.api.config.LockCraftingMode;
import ae2.api.config.PatternProviderBlockingType;
import ae2.api.config.PatternProviderInsertionMode;
import ae2.api.config.PatternProviderOutputSideMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.slot.AppEngSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.helpers.patternprovider.PatternProviderCapacity;
import ae2.helpers.patternprovider.PatternProviderLogic;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.helpers.patternprovider.PatternProviderReturnInventory;
import ae2.util.ConfigGuiInventory;
import ae2.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ContainerPatternProvider extends AEBaseContainer
    implements IConfigurableObject, PatternModifierPanel.Host {

    public static final String ACTION_SET_PAGE = "setPage";
    @GuiSync(12)
    public final boolean remote;
    private final PatternProviderLogic logic;
    private final List<AppEngSlot> patternSlots = new ObjectArrayList<>();
    private final PatternModifierPanel patternModifierPanel;
    @GuiSync(3)
    public BlockingMode blockingMode = BlockingMode.NO;
    @GuiSync(4)
    public YesNo showInAccessTerminal = YesNo.YES;
    @GuiSync(5)
    public LockCraftingMode lockCraftingMode = LockCraftingMode.NONE;
    @GuiSync(6)
    public LockCraftingMode craftingLockedReason = LockCraftingMode.NONE;
    @GuiSync(7)
    public GenericStack unlockStack;
    @GuiSync(8)
    public PatternProviderBlockingType blockingType = PatternProviderBlockingType.NORMAL;
    @GuiSync(14)
    public PatternProviderInsertionMode insertionMode = PatternProviderInsertionMode.DEFAULT;
    @GuiSync(15)
    public PatternProviderOutputSideMode outputSideMode = PatternProviderOutputSideMode.SINGLE_SIDE;
    @GuiSync(9)
    public int activePatternSlots = 9;
    @GuiSync(10)
    public int currentPage;
    @GuiSync(11)
    public int pageCount = 1;
    @GuiSync(13)
    public boolean patternModifierPanelAvailable;

    public ContainerPatternProvider(InventoryPlayer playerInventory, PatternProviderLogicHost host) {
        super(playerInventory, host);
        this.logic = host.getLogic();
        this.remote = playerInventory.player.openContainer instanceof ContainerPatternAccessTerm;
        this.registerClientAction(ACTION_SET_PAGE, Integer.class, this::setPage);

        AppEngInternalInventory patternInventory = this.logic.getPatternInv();
        for (int index = 0; index < patternInventory.size(); index++) {
            AppEngSlot slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.PROVIDER_PATTERN,
                patternInventory, index);
            this.patternSlots.add(slot);
            this.addSlot(slot, SlotSemantics.ENCODED_PATTERN);
        }

        this.setupUpgrades(this.logic.getUpgrades());

        ConfigGuiInventory returnInventory = this.logic.getReturnInv().createGuiWrapper();
        for (int index = 0; index < PatternProviderReturnInventory.NUMBER_OF_SLOTS
            && index < returnInventory.size(); index++) {
            this.addSlot(new AppEngSlot(returnInventory, index), SlotSemantics.STORAGE);
        }

        this.addPlayerInventorySlots(8, 163);
        this.patternModifierPanel = new PatternModifierPanel(this);
        this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();
        this.updatePatternSlotState();
    }

    protected boolean canInteractiveDistance(@NonNull EntityPlayer player, @NonNull TileEntity tileEntityHost) {
        return remote ? tileEntityHost.hasWorld() && tileEntityHost.getWorld().isBlockLoaded(tileEntityHost.getPos()) : super.canInteractiveDistance(player, tileEntityHost);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.blockingMode = this.logic.getConfigManager().getSetting(Settings.BLOCKING_MODE);
            this.blockingType = this.logic.getConfigManager().getSetting(Settings.PATTERN_PROVIDER_BLOCKING_TYPE);
            this.insertionMode = this.logic.getConfigManager().getSetting(Settings.PATTERN_PROVIDER_INSERTION_MODE);
            this.outputSideMode = this.logic.getConfigManager().getSetting(Settings.PATTERN_PROVIDER_OUTPUT_SIDE_MODE);
            this.showInAccessTerminal = this.logic.getConfigManager().getSetting(Settings.PATTERN_ACCESS_TERMINAL);
            this.lockCraftingMode = this.logic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE);
            this.craftingLockedReason = this.logic.getCraftingLockedReason();
            this.unlockStack = this.logic.getUnlockStack();
            this.activePatternSlots = this.logic.getActivePatternSlots();
            this.pageCount = this.logic.getPatternPageCount();
            this.currentPage = Math.min(this.currentPage, this.pageCount - 1);
            this.patternModifierPanelAvailable = this.patternModifierPanel.isAvailable();
        }

        this.updatePatternSlotState();
        super.broadcastChanges();
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
            && isProviderPattern(stack)
            && !hasSamePatternInOtherSlot(appEngSlot, stack);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (!isClientSide() && index >= 0 && index < this.inventorySlots.size()) {
            Slot sourceSlot = this.inventorySlots.get(index);
            if (sourceSlot != null && isPlayerSideSlot(sourceSlot) && isProviderPattern(sourceSlot.getStack())) {
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
    public IConfigManager getConfigManager() {
        return this.logic.getConfigManager();
    }

    public PatternProviderLogic getLogic() {
        return this.logic;
    }

    public BlockingMode getBlockingMode() {
        return this.blockingMode;
    }

    public PatternProviderBlockingType getBlockingType() {
        return this.blockingType;
    }

    public PatternProviderInsertionMode getInsertionMode() {
        return this.insertionMode;
    }

    public PatternProviderOutputSideMode getOutputSideMode() {
        return this.outputSideMode;
    }

    public YesNo getShowInAccessTerminal() {
        return this.showInAccessTerminal;
    }

    public LockCraftingMode getLockCraftingMode() {
        return this.lockCraftingMode;
    }

    public LockCraftingMode getCraftingLockedReason() {
        return this.craftingLockedReason;
    }

    public GenericStack getUnlockStack() {
        return this.unlockStack;
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

    public void setPage(int page) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PAGE, page);
            return;
        }
        this.currentPage = Math.clamp(page, 0, this.logic.getPatternPageCount() - 1);
        this.updatePatternSlotState();
        this.detectAndSendChanges();
    }

    private void updatePatternSlotState() {
        for (AppEngSlot slot : this.patternSlots) {
            boolean enabled = isPatternSlotVisible(slot);
            slot.setSlotEnabled(enabled);
            slot.setActive(enabled);
        }
        this.patternModifierPanel.updateSlotState(this.patternModifierPanelAvailable);
    }

    public void updatePatternModifierPanelVisibleSlots(boolean visible) {
        this.patternModifierPanel.updateSlotState(visible && this.patternModifierPanelAvailable);
    }

    private boolean isPatternSlotVisible(AppEngSlot slot) {
        return slot.getSlotIndex() < this.activePatternSlots
            && PatternProviderCapacity.isPatternSlotOnPage(slot.getSlotIndex(), this.currentPage);
    }

    private boolean hasSamePattern(ItemStack stack) {
        AEItemKey pattern = AEItemKey.of(stack);
        return pattern != null && this.logic.containsPattern(pattern);
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

    private boolean isProviderPattern(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, getPlayer().world);
        return details != null && !(details instanceof IAssemblerPattern);
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
