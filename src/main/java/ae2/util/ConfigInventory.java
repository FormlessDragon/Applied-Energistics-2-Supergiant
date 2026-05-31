package ae2.util;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKeyTypes;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.AEKeySlotFilter;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.me.helpers.BaseActionSource;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Configuration inventories contain a set of {@link AEKey} references that configure how certain aspects of a machine
 * work.
 * <p/>
 * They can expose an {@link ItemStack} based wrapper that can be used as backing for
 * {@link ae2.container.slot.FakeSlot} in {@link ae2.container.AEBaseContainer}.
 * <p/>
 * Primarily their role beyond their base class {@link GenericStackInv} is enforcing the configured filter even on
 * returned keys, not just when setting them.
 */
public class ConfigInventory extends GenericStackInv {
    private static final ConfigInventory EMPTY_TYPES = ConfigInventory.configTypes(0).build();
    private final boolean allowOverstacking;

    protected ConfigInventory(Set<AEKeyType> supportedTypes, @Nullable AEKeySlotFilter slotFilter, Mode mode, int size,
                              @Nullable Runnable listener, boolean allowOverstacking) {
        super(supportedTypes, listener, mode, size);
        this.allowOverstacking = allowOverstacking;
        setFilter(slotFilter);
    }

    public static ConfigInventory emptyTypes() {
        return EMPTY_TYPES;
    }

    public static Builder configTypes(int size) {
        return new Builder(Mode.CONFIG_TYPES, size);
    }

    public static Builder configStacks(int size) {
        return new Builder(Mode.CONFIG_STACKS, size);
    }

    public static Builder storage(int size) {
        return new Builder(Mode.STORAGE, size);
    }

    @Nullable
    @Override
    public GenericStack getStack(int slot) {
        var stack = super.getStack(slot);
        if (stack != null && !isSupportedType(stack.what())) {
            setStack(slot, null);
            stack = null;
        }
        return stack;
    }

    @Nullable
    @Override
    public AEKey getKey(int slot) {
        var key = super.getKey(slot);
        if (key == null) {
            return null;
        }
        if (!isSupportedType(key)) {
            setStack(slot, null);
            key = null;
        }
        return key;
    }

    public Set<AEKey> keySet() {
        var result = new it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet<AEKey>();
        for (int i = 0; i < stacks.length; i++) {
            var what = getKey(i);
            if (what != null) {
                result.add(what);
            }
        }
        return result;
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        if (stack != null) {
            if (!isSupportedType(stack.what())) {
                return;
            }
            boolean typesOnly = mode == Mode.CONFIG_TYPES;
            if (typesOnly && stack.amount() != 0) {
                stack = new GenericStack(stack.what(), 0);
            } else if (!typesOnly && stack.amount() <= 0) {
                if (mode == Mode.CONFIG_STACKS && getStack(slot) == null) {
                    stack = new GenericStack(stack.what(), 1);
                } else {
                    stack = null;
                }
            }
        }
        super.setStack(slot, stack);
    }

    @Override
    public long getMaxAmount(AEKey key) {
        if (allowOverstacking) {
            return getCapacity(key.getType());
        }
        return super.getMaxAmount(key);
    }

    @Override
    public ConfigGuiInventory createGuiWrapper() {
        return super.createGuiWrapper();
    }

    public ConfigInventory addFilter(Item item) {
        addFilter(AEItemKey.of(item));
        return this;
    }

    public ConfigInventory addFilter(Fluid fluid) {
        addFilter(AEFluidKey.of(fluid));
        return this;
    }

    public ConfigInventory addFilter(AEKey what) {
        Preconditions.checkState(getMode() == Mode.CONFIG_TYPES);
        insert(what, 1, Actionable.MODULATE, new BaseActionSource());
        return this;
    }

    public static final class Builder {
        private final Mode mode;
        private final int size;
        private Set<AEKeyType> supportedTypes = AEKeyTypes.getAll();
        @Nullable
        private AEKeySlotFilter slotFilter;
        @Nullable
        private Runnable changeListener;
        private boolean allowOverstacking;

        private Builder(Mode mode, int size) {
            this.mode = mode;
            this.size = size;
        }

        public Builder supportedType(AEKeyType type) {
            this.supportedTypes = Set.of(type);
            return this;
        }

        public Builder supportedTypes(AEKeyType type, AEKeyType... moreTypes) {
            if (moreTypes.length == 0) {
                return supportedType(type);
            }
            this.supportedTypes = new ObjectOpenHashSet<>(1 + moreTypes.length);
            this.supportedTypes.add(type);
            Collections.addAll(this.supportedTypes, moreTypes);
            return this;
        }

        public Builder supportedTypes(Collection<AEKeyType> types) {
            if (types.isEmpty()) {
                throw new IllegalArgumentException("Configuration inventories must support at least one key type");
            }
            this.supportedTypes = Set.copyOf(types);
            return this;
        }

        public Builder slotFilter(AEKeySlotFilter slotFilter) {
            this.slotFilter = slotFilter;
            return this;
        }

        public Builder slotFilter(Predicate<AEKey> filter) {
            this.slotFilter = (slot, what) -> filter.apply(what);
            return this;
        }

        public Builder changeListener(Runnable changeListener) {
            this.changeListener = changeListener;
            return this;
        }

        public Builder allowOverstacking(boolean enable) {
            this.allowOverstacking = enable;
            return this;
        }

        public ConfigInventory build() {
            return new ConfigInventory(
                supportedTypes,
                slotFilter,
                mode,
                size,
                changeListener,
                allowOverstacking);
        }
    }
}
