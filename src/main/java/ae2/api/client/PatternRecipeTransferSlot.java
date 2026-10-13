package ae2.api.client;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SideOnly(Side.CLIENT)
public final class PatternRecipeTransferSlot {

    private final int slot;
    @Nullable
    private GenericStack stack;
    private final List<AEKey> candidates = new ObjectArrayList<>();

    public PatternRecipeTransferSlot(int slot, @Nullable GenericStack stack) {
        this(slot, stack, null);
    }

    public PatternRecipeTransferSlot(int slot, @Nullable GenericStack stack, @Nullable List<AEKey> candidates) {
        this.slot = slot;
        setStack(stack);
        if (candidates != null) {
            for (AEKey candidate : candidates) {
                addCandidate(candidate);
            }
        }
    }

    public int getSlot() {
        return this.slot;
    }

    @Nullable
    public GenericStack getStack() {
        return this.stack;
    }

    public void setStack(@Nullable GenericStack stack) {
        this.stack = isValid(stack) ? stack : null;
        if (this.stack != null) {
            addCandidate(this.stack.what());
        }
    }

    public boolean isEmpty() {
        return this.stack == null;
    }

    public void clear() {
        this.stack = null;
    }

    public List<AEKey> getCandidates() {
        return this.candidates;
    }

    public void addCandidate(@Nullable AEKey candidate) {
        if (candidate != null && !this.candidates.contains(candidate)) {
            this.candidates.add(candidate);
        }
    }

    private static boolean isValid(@Nullable GenericStack stack) {
        return stack != null && stack.what() != null && stack.amount() > 0;
    }
}
