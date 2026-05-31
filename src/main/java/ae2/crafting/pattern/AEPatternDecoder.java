package ae2.crafting.pattern;

import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.IPatternDetailsDecoder;
import ae2.api.stacks.AEItemKey;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class AEPatternDecoder implements IPatternDetailsDecoder {
    public static final AEPatternDecoder INSTANCE = new AEPatternDecoder();

    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        return stack.getItem() instanceof EncodedPatternItem;
    }

    @Nullable
    @Override
    public IPatternDetails decodePattern(AEItemKey what, World level) {
        if (level == null || what == null || !(what.getItem() instanceof EncodedPatternItem<?> encodedPatternItem)) {
            return null;
        }

        return encodedPatternItem.decode(what, level);
    }
}
