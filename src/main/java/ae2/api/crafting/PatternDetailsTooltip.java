package ae2.api.crafting;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class PatternDetailsTooltip {
    public static final ITextComponent OUTPUT_TEXT_CRAFTS = GuiText.Crafts.text();

    public static final ITextComponent OUTPUT_TEXT_PRODUCES = GuiText.Produces.text();
    private final ObjectList<Property> additionalProperties = new ObjectArrayList<>();
    private final ObjectList<GenericStack> inputs = new ObjectArrayList<>();
    private final ObjectList<GenericStack> outputs = new ObjectArrayList<>();
    private ITextComponent outputMethod;

    public PatternDetailsTooltip(ITextComponent outputMethod) {
        setOutputMethod(outputMethod);
    }

    public List<Property> getProperties() {
        return additionalProperties;
    }

    public List<GenericStack> getInputs() {
        return inputs;
    }

    public List<GenericStack> getOutputs() {
        return outputs;
    }

    public void addInput(AEKey what, long amount) {
        inputs.add(new GenericStack(what, amount));
    }

    public void addInput(GenericStack stack) {
        inputs.add(new GenericStack(stack.what(), stack.amount()));
    }

    public void addOutput(AEKey what, long amount) {
        outputs.add(new GenericStack(what, amount));
    }

    public void addOutput(GenericStack stack) {
        outputs.add(new GenericStack(stack.what(), stack.amount()));
    }

    public void addProperty(ITextComponent name, ITextComponent value) {
        this.additionalProperties.add(new Property(name, value));
    }

    public void addProperty(ITextComponent description) {
        this.additionalProperties.add(new Property(description, null));
    }

    public void addInputsAndOutputs(IPatternDetails details) {
        for (IPatternDetails.IInput input : details.getInputs()) {
            if (input == null) {
                continue;
            }

            addInput(input.possibleInputs()[0].what(),
                input.possibleInputs()[0].amount() * input.getMultiplier());
        }

        for (GenericStack output : details.getOutputs()) {
            if (output == null) {
                continue;
            }

            addOutput(output.what(), output.amount());
        }
    }

    public ITextComponent getOutputMethod() {
        return outputMethod;
    }

    public void setOutputMethod(ITextComponent outputMethod) {
        this.outputMethod = Objects.requireNonNull(outputMethod, "outputMethod");
    }

    public record Property(ITextComponent name, @Nullable ITextComponent tooltip) {
    }
}
