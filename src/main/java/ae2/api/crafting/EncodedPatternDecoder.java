package ae2.api.crafting;

import ae2.api.stacks.AEItemKey;
import net.minecraft.world.World;

@FunctionalInterface
public interface EncodedPatternDecoder<T extends IPatternDetails> {
    T decode(AEItemKey what, World level);
}
