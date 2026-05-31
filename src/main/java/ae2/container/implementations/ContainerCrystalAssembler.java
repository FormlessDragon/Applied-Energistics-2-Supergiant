package ae2.container.implementations;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.util.IConfigManager;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.interfaces.IProgressProvider;
import ae2.container.slot.AppEngSlot;
import ae2.container.slot.OutputSlot;
import ae2.tile.misc.TileCrystalAssembler;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerCrystalAssembler extends UpgradeableContainer<TileCrystalAssembler> implements IProgressProvider {
    private final AppEngSlot tankSlot;

    @GuiSync(3)
    public int processingTime = -1;
    @GuiSync(8)
    public YesNo autoExport = YesNo.NO;

    public ContainerCrystalAssembler(InventoryPlayer ip, TileCrystalAssembler host) {
        super(ip, host);
        this.tankSlot = (AppEngSlot) getSlots(SlotSemantics.STORAGE).getFirst();
    }

    @Override
    protected void setupInventorySlots() {
        for (int x = 0; x < TileCrystalAssembler.INPUT_SLOTS; x++) {
            addSlot(new AppEngSlot(getHost().getInput(), x, 0, 0), SlotSemantics.MACHINE_INPUT);
        }
        addSlot(new AppEngSlot(getHost().getTank().createGuiWrapper(), 0, 0, 0), SlotSemantics.STORAGE);
        addSlot(new OutputSlot(getHost().getOutput(), 0, 0, 0), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.autoExport = cm.getSetting(Settings.AUTO_EXPORT);
    }

    @Override
    protected void standardDetectAndSendChanges() {
        if (isServerSide()) {
            this.processingTime = getHost().getProcessingTime();
        }
        super.standardDetectAndSendChanges();
    }

    @Override
    public int getCurrentProgress() {
        return this.processingTime;
    }

    @Override
    public int getMaxProgress() {
        return getHost().getMaxProcessingTime();
    }

    public YesNo getAutoExport() {
        return this.autoExport;
    }

    public boolean isTankSlot(net.minecraft.inventory.Slot slot) {
        return slot == this.tankSlot;
    }
}
