package ae2.api.storage;

import ae2.api.stacks.AEKey;

class NoOpKeyFilter implements AEKeyFilter {
    static final NoOpKeyFilter INSTANCE = new NoOpKeyFilter();

    @Override
    public boolean matches(AEKey what) {
        return true;
    }

}
