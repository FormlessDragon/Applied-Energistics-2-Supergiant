package ae2.api.client;

import ae2.container.me.patternencode.ContainerPatternEncodingTerm;
import ae2.parts.encoding.EncodingMode;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@SideOnly(Side.CLIENT)
@Cancelable
public final class PatternRecipeTransferEvent extends Event {

    private final ContainerPatternEncodingTerm container;
    private final EncodingMode mode;
    private final String recipeCategoryUid;
    @Nullable
    private final String recipeCategoryTitle;
    @Nullable
    private final ResourceLocation craftingRecipeId;
    private final List<PatternRecipeTransferSlot> inputSlots;
    private final List<PatternRecipeTransferSlot> outputSlots;

    public PatternRecipeTransferEvent(ContainerPatternEncodingTerm container,
                                      EncodingMode mode,
                                      String recipeCategoryUid,
                                      @Nullable String recipeCategoryTitle,
                                      @Nullable ResourceLocation craftingRecipeId,
                                      List<PatternRecipeTransferSlot> inputSlots,
                                      List<PatternRecipeTransferSlot> outputSlots) {
        this.container = Objects.requireNonNull(container, "container");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.recipeCategoryUid = Objects.requireNonNull(recipeCategoryUid, "recipeCategoryUid");
        this.recipeCategoryTitle = recipeCategoryTitle;
        this.craftingRecipeId = craftingRecipeId;
        this.inputSlots = Collections.unmodifiableList(Objects.requireNonNull(inputSlots, "inputSlots"));
        this.outputSlots = Collections.unmodifiableList(Objects.requireNonNull(outputSlots, "outputSlots"));
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    public ContainerPatternEncodingTerm getContainer() {
        return this.container;
    }

    public EncodingMode getMode() {
        return this.mode;
    }

    public boolean isCrafting() {
        return this.mode == EncodingMode.CRAFTING;
    }

    public String getRecipeCategoryUid() {
        return this.recipeCategoryUid;
    }

    @Nullable
    public String getRecipeCategoryTitle() {
        return this.recipeCategoryTitle;
    }

    @Nullable
    public ResourceLocation getCraftingRecipeId() {
        return this.craftingRecipeId;
    }

    public List<PatternRecipeTransferSlot> getInputSlots() {
        return this.inputSlots;
    }

    public List<PatternRecipeTransferSlot> getOutputSlots() {
        return this.outputSlots;
    }
}
