package ae2.parts.automation;

import ae2.api.stacks.AEKey;

interface StorageReader {
    long getCurrentStock(AEKey what);
}
