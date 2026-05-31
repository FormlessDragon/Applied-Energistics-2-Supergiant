package ae2.api.storage;

import ae2.api.stacks.AEKey;

@FunctionalInterface
public interface AEKeyFilter {
    static AEKeyFilter none() {
        return NoOpKeyFilter.INSTANCE;
    }

    boolean matches(AEKey what);
}
