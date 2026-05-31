package ae2.parts.encoding;

import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.util.IConfigManagerBuilder;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.helpers.IPatternTerminalGuiHost;
import ae2.helpers.IPatternTerminalLogicHost;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import ae2.parts.reporting.AbstractTerminalPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.List;

public class PatternEncodingTerminalPart extends AbstractTerminalPart
    implements IPatternTerminalLogicHost, IPatternTerminalGuiHost {
    @PartModels
    public static final ResourceLocation MODEL_OFF = AppEng.makeId("part/pattern_encoding_terminal_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = AppEng.makeId("part/pattern_encoding_terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);

    public PatternEncodingTerminalPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.PATTERN_AUTO_FILL, YesNo.NO);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (var stack : this.logic.getBlankPatternInv()) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        for (var stack : this.logic.getEncodedPatternInv()) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.logic.getBlankPatternInv().clear();
        this.logic.getEncodedPatternInv().clear();
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.logic.readFromNBT(data);
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.logic.writeToNBT(data);
    }

    @Override
    public GuiIds.GuiKey getGuiKey(EntityPlayer player) {
        return GuiIds.GuiKey.PATTERN_ENCODING_TERMINAL;
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public PatternEncodingLogic getLogic() {
        return this.logic;
    }

    @Override
    public void markForSave() {
        getHost().markForSave();
    }
}
