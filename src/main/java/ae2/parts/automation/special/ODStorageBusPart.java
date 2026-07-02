package ae2.parts.automation.special;

import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import ae2.parts.storagebus.StorageBusPart;
import ae2.util.SettingsFrom;
import ae2.util.prioritylist.IPartitionList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class ODStorageBusPart extends StorageBusPart implements ODFilterHost {
    private static final ResourceLocation MODEL_BASE = AppEng.makeId("part/od_storage_bus_base");

    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, StorageBusPartModels.OFF);

    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, StorageBusPartModels.ON);

    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, StorageBusPartModels.HAS_CHANNEL);

    private String whiteExpression = "";
    private String blackExpression = "";

    public ODStorageBusPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.whiteExpression = data.getString("odWhite");
        this.blackExpression = data.getString("odBlack");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setString("odWhite", this.whiteExpression);
        data.setString("odBlack", this.blackExpression);
    }

    @Override
    public void exportSettings(SettingsFrom mode, NBTTagCompound output) {
        super.exportSettings(mode, output);
        if (mode == SettingsFrom.MEMORY_CARD) {
            exportODFilterMemoryCardSettings(output);
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            importODFilterMemoryCardSettings(input);
        }
    }

    @Override
    protected IPartitionList createFilter() {
        return new ODPriorityList(this.whiteExpression, this.blackExpression);
    }

    @Override
    public String getODFilter(boolean whitelist) {
        return whitelist ? this.whiteExpression : this.blackExpression;
    }

    @Override
    public void setODFilter(String expression, boolean whitelist) {
        expression = expression == null ? "" : expression;
        if (whitelist) {
            if (!expression.equals(this.whiteExpression)) {
                this.whiteExpression = expression;
                onConfigurationChanged();
                getHost().markForSave();
            }
        } else if (!expression.equals(this.blackExpression)) {
            this.blackExpression = expression;
            onConfigurationChanged();
            getHost().markForSave();
        }
    }

    @Override
    public GuiIds.GuiKey getGuiKey() {
        return GuiIds.GuiKey.OD_STORAGE_BUS;
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        }
        return MODELS_OFF;
    }
}
