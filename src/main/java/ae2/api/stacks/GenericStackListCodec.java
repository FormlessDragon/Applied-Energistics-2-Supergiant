package ae2.api.stacks;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.NBTTagList;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class GenericStackListCodec {
    private GenericStackListCodec() {
    }

    public static NBTTagList encode(List<@Nullable GenericStack> input) {
        Objects.requireNonNull(input, "input");

        var output = new NBTTagList();
        for (var genericStack : input) {
            output.appendTag(GenericStack.writeTag(genericStack));
        }

        return output;
    }

    public static List<@Nullable GenericStack> decode(NBTTagList input) {
        Objects.requireNonNull(input, "input");

        List<GenericStack> output = new ObjectArrayList<>(input.tagCount());
        for (int i = 0; i < input.tagCount(); i++) {
            var tag = input.getCompoundTagAt(i);
            try {
                output.add(GenericStack.readTag(tag));
            } catch (RuntimeException e) {
                output.add(GenericStack.createMissingContentStack(tag, "Failed to deserialize GenericStack"));
            }
        }

        return Collections.unmodifiableList(output);
    }
}
