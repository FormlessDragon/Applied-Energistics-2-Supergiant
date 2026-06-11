package ae2.parts.automation;

import ae2.api.behaviors.StackImportStrategy;
import ae2.api.networking.IGrid;
import ae2.api.parts.IPartItem;
import ae2.api.util.KeyTypeSelection;
import ae2.api.util.KeyTypeSelectionHost;
import ae2.container.GuiIds;
import ae2.core.definitions.AEItems;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.Nullable;

public class ImportExportBusPart extends ExportBusPart implements KeyTypeSelectionHost {
    private static final StackImportStrategy NOOP_IMPORT_STRATEGY = context -> {
        if (context == null) {
            return false;
        }
        return false;
    };

    private final KeyTypeSelection keyTypeSelection;
    @Nullable
    private StackImportStrategy importStrategy;

    public ImportExportBusPart(IPartItem<?> partItem) {
        super(partItem);
        this.keyTypeSelection = new KeyTypeSelection(() -> {
            getHost().markForSave();
            importStrategy = null;
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }, StackWorldBehaviors.hasImportStrategyTypeFilter());
    }

    protected StackImportStrategy getImportStrategy() {
        if (this.importStrategy == null) {
            var self = this.getHost().getTileEntity();
            var side = getSide();
            if (side == null) {
                return NOOP_IMPORT_STRATEGY;
            }
            var fromPos = self.getPos().offset(side);
            var fromSide = side.getOpposite();
            importStrategy = StackWorldBehaviors.createImportFacade((WorldServer) getLevel(), fromPos, fromSide,
                keyTypeSelection.enabledPredicate());
        }
        return this.importStrategy;
    }

    @Override
    protected boolean doBusWork(IGrid grid) {
        boolean exportWork = super.doBusWork(grid);
        var context = new FilteredImportStackTransferContext(grid.getStorageService(), grid.getEnergyService(),
            this.source, getOperationsPerTick(), getFilter());
        context.setInverted(this.isUpgradedWith(AEItems.INVERTER_CARD));
        getImportStrategy().transfer(context);
        return exportWork || context.hasDoneWork();
    }

    @Override
    protected GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.IMPORT_EXPORT_BUS;
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        keyTypeSelection.readFromNBT(extra);
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        keyTypeSelection.writeToNBT(extra);
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return keyTypeSelection;
    }
}
