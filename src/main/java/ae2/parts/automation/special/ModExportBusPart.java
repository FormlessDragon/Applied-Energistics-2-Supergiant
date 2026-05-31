package ae2.parts.automation.special;

import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.container.GuiIds.GuiKey;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import ae2.util.prioritylist.IPartitionList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class ModExportBusPart extends SpecialExportBusPart implements ModFilterHost {
    private static final ResourceLocation MODEL_BASE = AppEng.makeId("part/mod_export_bus_base");

    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, ExportBusPartModels.OFF);

    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, ExportBusPartModels.ON);

    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, ExportBusPartModels.HAS_CHANNEL);

    private String modExpression = "";

    public ModExportBusPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.modExpression = extra.getString("modid");
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        extra.setString("modid", this.modExpression);
    }

    @Override
    public String getModFilter() {
        return this.modExpression;
    }

    @Override
    public void setModFilter(String expression) {
        expression = expression == null ? "" : expression;
        if (!expression.equals(this.modExpression)) {
            this.modExpression = expression;
            invalidateSpecialFilter();
        }
    }

    @Override
    protected IPartitionList createSpecialFilter() {
        return new ModPriorityList(this.modExpression);
    }

    @Override
    protected GuiKey getGuiKey() {
        return GuiKey.MOD_EXPORT_BUS;
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
