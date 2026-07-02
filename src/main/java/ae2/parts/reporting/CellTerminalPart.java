package ae2.parts.reporting;

import ae2.api.cellterminal.CellTerminalContainerHost;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGridNode;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.cellterminal.internal.CellTerminalTempCells;
import ae2.cellterminal.server.CellTerminalSubnetLedger;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import ae2.util.inv.AppEngInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.List;

public class CellTerminalPart extends AbstractTerminalPart implements CellTerminalContainerHost {
    @PartModels
    public static final ResourceLocation MODEL_OFF = AppEng.makeId("part/cell_terminal_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = AppEng.makeId("part/cell_terminal_on");
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);
    private static final String TEMP_CELL_NBT_TAG = "tempCell";
    private static final String SUBNET_LEDGER_NBT_TAG = "subnetLedger";
    private final AppEngInternalInventory tempCellStorage = new AppEngInternalInventory(this,
        CellTerminalTempCells.SLOT_COUNT, 1);
    private CellTerminalSubnetLedger subnetLedger = new CellTerminalSubnetLedger();

    public CellTerminalPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public GuiIds.GuiKey getGuiKey(EntityPlayer player) {
        return GuiIds.GuiKey.CELL_TERMINAL;
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public IGridNode getGridNode() {
        return getMainNode().getNode();
    }

    @Override
    public InternalInventory getTempCellStorage() {
        return this.tempCellStorage;
    }

    @Override
    public NBTTagCompound loadCellTerminalSubnetLedgerTag() {
        return this.subnetLedger.writeToTag();
    }

    @Override
    public void saveCellTerminalSubnetLedgerTag(NBTTagCompound tag) {
        this.subnetLedger = CellTerminalSubnetLedger.fromTag(tag);
        getHost().markForSave();
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (ItemStack stack : this.tempCellStorage) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.tempCellStorage.clear();
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.tempCellStorage.readFromNBT(data, TEMP_CELL_NBT_TAG);
        this.subnetLedger = CellTerminalSubnetLedger.fromTag(data.getCompoundTag(SUBNET_LEDGER_NBT_TAG));
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.tempCellStorage.writeToNBT(data, TEMP_CELL_NBT_TAG);
        data.setTag(SUBNET_LEDGER_NBT_TAG, this.subnetLedger.writeToTag());
    }
}
