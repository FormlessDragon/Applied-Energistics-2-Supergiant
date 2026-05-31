package ae2.api.storage;

import ae2.api.stacks.AEKey;

@FunctionalInterface
public interface AEKeySlotFilter {
    boolean isAllowed(int slot, AEKey what);
}
