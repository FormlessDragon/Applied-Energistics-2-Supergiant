package ae2.crafting.execution;

import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.crafting.UnsuitableCpus;
import ae2.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

public record CraftingSubmitResult(@Nullable ICraftingLink link,
                                   @Nullable CraftingSubmitErrorCode errorCode,
                                   @Nullable Object errorDetail) implements ICraftingSubmitResult {

    public static final ICraftingSubmitResult NO_CPU_FOUND = simpleError(CraftingSubmitErrorCode.NO_CPU_FOUND);
    public static final ICraftingSubmitResult INCOMPLETE_PLAN = simpleError(CraftingSubmitErrorCode.INCOMPLETE_PLAN);
    public static final ICraftingSubmitResult CPU_BUSY = simpleError(CraftingSubmitErrorCode.CPU_BUSY);
    public static final ICraftingSubmitResult CPU_OFFLINE = simpleError(CraftingSubmitErrorCode.CPU_OFFLINE);
    public static final ICraftingSubmitResult CPU_TOO_SMALL = simpleError(CraftingSubmitErrorCode.CPU_TOO_SMALL);
    public static final ICraftingSubmitResult NO_CRAFTING_PATTERN =
        simpleError(CraftingSubmitErrorCode.NO_CRAFTING_PATTERN);

    public static ICraftingSubmitResult successful(@Nullable ICraftingLink link) {
        return new CraftingSubmitResult(link, null, null);
    }

    public static ICraftingSubmitResult simpleError(CraftingSubmitErrorCode code) {
        return new CraftingSubmitResult(null, code, null);
    }

    public static ICraftingSubmitResult missingIngredient(GenericStack missingIngredient) {
        return new CraftingSubmitResult(null, CraftingSubmitErrorCode.MISSING_INGREDIENT, missingIngredient);
    }

    public static ICraftingSubmitResult noSuitableCpu(UnsuitableCpus unsuitableCpus) {
        return new CraftingSubmitResult(null, CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND, unsuitableCpus);
    }
}
