package ae2.api.networking.crafting;

import ae2.api.stacks.GenericStack;

public record CraftingJobStatus(GenericStack crafting, long totalItems, long progress, long elapsedTimeNanos) {
}
