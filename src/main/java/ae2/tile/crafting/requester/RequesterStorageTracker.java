package ae2.tile.crafting.requester;

import ae2.api.networking.IStackWatcher;
import ae2.api.networking.storage.IStorageWatcherNode;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.IntConsumer;

public final class RequesterStorageTracker implements IStorageWatcherNode {
    private static final String BUFFERED = "buffered";
    private static final String TOTAL = "total";
    private static final String PENDING_INSERTION = "pendingInsertion";
    private static final String KNOWN = "known";

    private final KeyCounter[] total;
    private final KeyCounter[] buffered;
    private final KeyCounter[] pendingInsertion;
    private final KeyCounter[] known;
    private final @Nullable AEKey[] watchedKeys;
    private final IntConsumer stackChangeListener;
    private @Nullable IStackWatcher stackWatcher;

    public RequesterStorageTracker(int size, IntConsumer stackChangeListener) {
        this.total = new KeyCounter[size];
        this.buffered = new KeyCounter[size];
        this.pendingInsertion = new KeyCounter[size];
        this.known = new KeyCounter[size];
        this.watchedKeys = new AEKey[size];
        this.stackChangeListener = stackChangeListener;
        for (int i = 0; i < size; i++) {
            this.total[i] = new KeyCounter();
            this.buffered[i] = new KeyCounter();
            this.pendingInsertion[i] = new KeyCounter();
            this.known[i] = new KeyCounter();
        }
    }

    private static NBTTagList writeCounter(KeyCounter counter) {
        var list = new NBTTagList();
        for (var entry : counter) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                list.appendTag(GenericStack.writeTag(new GenericStack(entry.getKey(), amount)));
            }
        }
        return list;
    }

    private static void readCounter(NBTTagList list, KeyCounter counter) {
        for (int entryIndex = 0; entryIndex < list.tagCount(); entryIndex++) {
            var stack = GenericStack.readTag(list.getCompoundTagAt(entryIndex));
            if (stack != null) {
                counter.add(stack.what(), stack.amount());
            }
        }
    }

    public void addPending(int slot, AEKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        buffered[slot].add(key, amount);
    }

    public long getPendingAmount(int slot, AEKey key) {
        return getBufferedAmount(slot, key) + getPendingInsertionAmount(slot, key);
    }

    public long getBufferedAmount(int slot, AEKey key) {
        return buffered[slot].get(key);
    }

    public long getPendingInsertionAmount(int slot, AEKey key) {
        return pendingInsertion[slot].get(key);
    }

    public long getRemainingTotalAmount(int slot, AEKey key) {
        return total[slot].get(key);
    }

    public long getKnownAmount(int slot, AEKey key) {
        return known[slot].get(key);
    }

    public @Nullable AEKey getBufferedKey(int slot) {
        return buffered[slot].getFirstKey();
    }

    public void setKnownAmount(int slot, AEKey key, long amount) {
        known[slot].set(key, Math.max(0, amount));
        pendingInsertion[slot].remove(key);
    }

    public void watch(int slot, @Nullable AEKey key) {
        if (Objects.equals(watchedKeys[slot], key)) {
            return;
        }
        watchedKeys[slot] = key;
        resetWatcher();
    }

    public void setTotalAmount(int slot, AEKey key, long amount) {
        total[slot].set(key, Math.max(0, amount));
        total[slot].removeZeros();
    }

    @Override
    public void updateWatcher(IStackWatcher newWatcher) {
        this.stackWatcher = Objects.requireNonNull(newWatcher);
        resetWatcher();
    }

    @Override
    public void onStackChange(AEKey what, long amount) {
        for (int slot = 0; slot < watchedKeys.length; slot++) {
            AEKey watchedKey = watchedKeys[slot];
            if (what.equals(watchedKey)
                || buffered[slot].get(what) > 0
                || pendingInsertion[slot].get(what) > 0
                || known[slot].get(what) > 0) {
                setKnownAmount(slot, what, amount);
                this.stackChangeListener.accept(slot);
            }
        }
    }

    public long computeAmountToCraft(int slot, Request request) {
        if (!request.isEnabled() || request.isEmpty()) {
            return 0;
        }

        AEKey key = request.getKey();
        if (key == null) {
            return 0;
        }

        long outstanding = getKnownAmount(slot, key) + getPendingAmount(slot, key);
        return outstanding < request.getAmount() ? request.getBatchSize() : 0;
    }

    public void markExported(int slot, AEKey key, long amount) {
        long exported = Math.clamp(amount, 0, getBufferedAmount(slot, key));
        if (exported <= 0) {
            return;
        }

        buffered[slot].remove(key, exported);
        buffered[slot].removeZeros();
        pendingInsertion[slot].add(key, exported);
        total[slot].remove(key, exported);
        total[slot].removeZeros();
    }

    public void clear(int slot) {
        total[slot].clear();
        buffered[slot].clear();
        pendingInsertion[slot].clear();
        known[slot].clear();
        watchedKeys[slot] = null;
        resetWatcher();
    }

    public NBTTagCompound writeToNBT() {
        var tag = new NBTTagCompound();
        for (int i = 0; i < buffered.length; i++) {
            var slotTag = new NBTTagCompound();
            slotTag.setTag(TOTAL, writeCounter(total[i]));
            slotTag.setTag(BUFFERED, writeCounter(buffered[i]));
            slotTag.setTag(PENDING_INSERTION, writeCounter(pendingInsertion[i]));
            slotTag.setTag(KNOWN, writeCounter(known[i]));
            tag.setTag(Integer.toString(i), slotTag);
        }
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        for (int i = 0; i < buffered.length; i++) {
            clear(i);
            String key = Integer.toString(i);
            if (tag.hasKey(key, 9)) {
                readCounter(tag.getTagList(key, 10), buffered[i]);
                continue;
            }
            if (!tag.hasKey(key, 10)) {
                continue;
            }

            var slotTag = tag.getCompoundTag(key);
            if (slotTag.hasKey(TOTAL, 9)) {
                readCounter(slotTag.getTagList(TOTAL, 10), total[i]);
            }
            if (slotTag.hasKey(BUFFERED, 9)) {
                readCounter(slotTag.getTagList(BUFFERED, 10), buffered[i]);
            }
            if (slotTag.hasKey(PENDING_INSERTION, 9)) {
                readCounter(slotTag.getTagList(PENDING_INSERTION, 10), pendingInsertion[i]);
            }
            if (slotTag.hasKey(KNOWN, 9)) {
                readCounter(slotTag.getTagList(KNOWN, 10), known[i]);
            }
        }
    }

    private void resetWatcher() {
        if (this.stackWatcher == null) {
            return;
        }

        this.stackWatcher.reset();
        for (AEKey watchedKey : watchedKeys) {
            if (watchedKey != null) {
                this.stackWatcher.add(watchedKey);
            }
        }
    }
}
