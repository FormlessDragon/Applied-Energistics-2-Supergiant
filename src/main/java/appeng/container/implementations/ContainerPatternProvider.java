package appeng.container.implementations;

import appeng.api.config.BlockingMode;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.PatternProviderBlockingType;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.container.SlotSemantics;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.RestrictedInputSlot;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.util.ConfigGuiInventory;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerPatternProvider extends AEBaseContainer implements IConfigurableObject {

    private final PatternProviderLogic logic;

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

    public ContainerPatternProvider( InventoryPlayer playerInventory, PatternProviderLogicHost host) {
        super(playerInventory, host);
        this.logic = host.getLogic();

        AppEngInternalInventory patternInventory = this.logic.getPatternInv();
        for (int index = 0; index < patternInventory.size(); index++) {
            this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.PROVIDER_PATTERN,
                patternInventory, index), SlotSemantics.ENCODED_PATTERN);
        }

        this.setupUpgrades(this.logic.getUpgrades());

        ConfigGuiInventory returnInventory = this.logic.getReturnInv().createGuiWrapper();
        for (int index = 0; index < PatternProviderReturnInventory.NUMBER_OF_SLOTS
            && index < returnInventory.size(); index++) {
            this.addSlot(new AppEngSlot(returnInventory, index), SlotSemantics.STORAGE);
        }

        this.addPlayerInventorySlots(8, 133);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.blockingMode = this.logic.getConfigManager().getSetting(Settings.BLOCKING_MODE);
            this.blockingType = this.logic.getConfigManager().getSetting(Settings.PATTERN_PROVIDER_BLOCKING_TYPE);
            this.showInAccessTerminal = this.logic.getConfigManager().getSetting(Settings.PATTERN_ACCESS_TERMINAL);
            this.lockCraftingMode = this.logic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE);
            this.craftingLockedReason = this.logic.getCraftingLockedReason();
            this.unlockStack = this.logic.getUnlockStack();
        }

        super.broadcastChanges();
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
}
