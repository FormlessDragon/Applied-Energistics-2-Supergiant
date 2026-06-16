package ae2.api.stacks;

import ae2.api.config.FuzzyMode;
import ae2.core.AELog;
import ae2.core.definitions.AEItems;
import ae2.items.misc.GenericResourcePackageItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Uniquely identifies something that "stacks" within an ME inventory.
 * <p/>
 * For example for items, this is the combination of an {@link net.minecraft.item.Item} and optional
 * {@link NBTTagCompound}. To account for common indexing scenarios, a key is (optionally) split into a primary and
 * secondary component, which serves two purposes:
 * <ul>
 * <li>Fuzzy cards allow setting filters for the primary component of a key, i.e. for an
 * {@link net.minecraft.item.Item}, while disregarding the compound tag.</li>
 * <li>When indexing resources, it is usually assumed that indexing by the primary key alone offers a good trade-off
 * between memory usage and lookup speed.</li>
 * </ul>
 */
public abstract class AEKey {
    public static final String TYPE_FIELD = "#t";
    private static final String MISSING_CONTENT_AEKEY_DATA = "missing_content_aekey_data";
    private static final String MISSING_CONTENT_ERROR = "missing_content_error";

    private ITextComponent cachedDisplayName;

    /**
     * Writes a generic, nullable key to the given buffer.
     */
    public static void writeOptionalKey(PacketBuffer buffer, @Nullable AEKey key) {
        buffer.writeBoolean(key != null);
        if (key != null) {
            writeKey(buffer, key);
        }
    }

    /**
     * Writes a generic key to the given buffer.
     */
    public static void writeKey(PacketBuffer buffer, AEKey key) {
        buffer.writeByte(key.getType().getRawId());
        key.writeToPacket(buffer);
    }

    /**
     * Reads a generic, nullable key from the given buffer.
     */
    @Nullable
    public static AEKey readOptionalKey(PacketBuffer buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }

        return readKey(buffer);
    }

    /**
     * Reads a generic key from the given buffer.
     */
    public static @Nullable AEKey readKey(PacketBuffer buffer) {
        int typeId = buffer.readByte();
        var keyType = AEKeyType.fromRawId(typeId);
        if (keyType == null) {
            AELog.warn("Received unknown key type id %d", typeId);
            return null;
        }
        return keyType.readFromPacket(buffer);
    }

    @Nullable
    public static AEKey fromTagGeneric(NBTTagCompound tag) {
        if (tag == null) {
            return null;
        }

        if (!tag.hasKey(TYPE_FIELD, Constants.NBT.TAG_STRING)) {
            return null;
        }

        var type = tag.getString(TYPE_FIELD);
        try {
            var keyType = AEKeyTypesInternal.get(new ResourceLocation(type));
            if (keyType == null) {
                return missingContentKey(tag, "No key type registered for id " + type);
            }
            var key = keyType.loadKeyFromTag(tag);
            return key != null ? key : missingContentKey(tag, "Failed to deserialize AE key " + type);
        } catch (Exception e) {
            AELog.debug("Tried to load an invalid key from NBT: %s", tag, e);
            return missingContentKey(tag, e.getMessage());
        }
    }

    private static AEKey missingContentKey(NBTTagCompound tag, String error) {
        var missingContent = AEItems.MISSING_CONTENT.stack();
        if (!missingContent.hasTagCompound()) {
            missingContent.setTagCompound(new NBTTagCompound());
        }
        var missingContentTag = Objects.requireNonNull(missingContent.getTagCompound());
        missingContentTag.setTag(MISSING_CONTENT_AEKEY_DATA, tag.copy());
        if (error != null) {
            missingContentTag.setString(MISSING_CONTENT_ERROR, error);
        }
        return Objects.requireNonNull(AEItemKey.of(missingContent));
    }

    /**
     * Same as {@link #toTag()}, but includes type information so that
     * {@link #fromTagGeneric(NBTTagCompound)} can restore this particular type of key withot knowing it.
     */
    public final NBTTagCompound toTagGeneric() {
        if (AEItems.MISSING_CONTENT.is(this)) {
            var originalTag = get(MISSING_CONTENT_AEKEY_DATA);
            if (originalTag instanceof NBTTagCompound compound) {
                return compound.copy();
            }
        }

        var tag = toTag();
        tag.setString(TYPE_FIELD, getType().getId().toString());
        return tag;
    }

    public abstract AEKeyType getType();

    public final int getAmountPerUnit() {
        return getType().getAmountPerUnit();
    }

    @Nullable
    public final String getUnitSymbol() {
        return getType().getUnitSymbol();
    }

    /**
     * @see AEKeyType#getAmountPerOperation()
     */
    public final int getAmountPerOperation() {
        return getType().getAmountPerOperation();
    }

    /**
     * @see AEKeyType#getAmountPerByte()
     */
    public final int getAmountPerByte() {
        return getType().getAmountPerByte();
    }

    /**
     * @see AEKeyType#formatAmount(long, AmountFormat)
     */
    public String formatAmount(long amount, AmountFormat format) {
        return getType().formatAmount(amount, format);
    }

    public abstract AEKey dropSecondary();

    /**
     * Serialized keys MUST NOT contain keys that start with <code>#</code>, because this prefix can be used to add
     * additional data into the same tag as the key.
     */
    public abstract NBTTagCompound toTag();

    public abstract Object getPrimaryKey();

    /**
     * @return If {@link #getFuzzySearchMaxValue()} is greater than 0, this is the value in the range of
     * [0,getFuzzyModeMaxValue] used to index keys by. Used by fuzzy mode search with percentage ranges.
     */
    public int getFuzzySearchValue() {
        return 0;
    }

    /**
     * @return The upper bound for values returned by {@link #getFuzzySearchValue()}. If it is equal to 0, no fuzzy
     * range-search is possible for this type of key.
     */
    public int getFuzzySearchMaxValue() {
        return 0;
    }

    /**
     * Tests if this and the given AE key are in the same fuzzy partition given a specific fuzzy matching mode.
     */
    public final boolean fuzzyEquals(AEKey other, FuzzyMode fuzzyMode) {
        if (other == null || other.getClass() != getClass()) {
            return false;
        }

        // For any fuzzy mode, the primary key (item, fluid) must still match
        if (getPrimaryKey() != other.getPrimaryKey()) {
            return false;
        }

        // If the type doesn't support fuzzy range search, it always behaves like IGNORE_ALL, which just ignores NBT
        if (!supportsFuzzyRangeSearch()) {
            return true;
        } else if (fuzzyMode == FuzzyMode.IGNORE_ALL) {
            return true;
        } else if (fuzzyMode == FuzzyMode.PERCENT_99) {
            return getFuzzySearchValue() > 0 == other.getFuzzySearchValue() > 0;
        } else {
            final float percentA = (float) getFuzzySearchValue() / getFuzzySearchMaxValue();
            final float percentB = (float) other.getFuzzySearchValue() / other.getFuzzySearchMaxValue();

            return percentA > fuzzyMode.breakPoint == percentB > fuzzyMode.breakPoint;
        }
    }

    /**
     * Checks if the given stack has the same key as this.
     *
     * @return False if stack is null, otherwise true iff the stacks key is equal to this.
     */
    public final boolean matches(@Nullable GenericStack stack) {
        return stack != null && stack.what().equals(this);
    }

    /**
     * @return The ID of the mod this resource belongs to.
     */
    public String getModId() {
        var id = getId();
        return id != null ? id.getNamespace() : "";
    }

    /**
     * @return The ID of the resource identified by this key.
     */
    @Nullable
    public abstract ResourceLocation getId();

    public abstract void writeToPacket(PacketBuffer data);

    @Nullable
    public abstract Object getReadOnlyStack();

    /**
     * Wraps a key in an ItemStack that can be unwrapped into a key later.
     */
    public ItemStack wrapForDisplayOrFilter() {
        return GenericStack.wrapInItemStack(this, 0);
    }

    /**
     * True to indicate that this type of {@link AEKey} supports range-based fuzzy search using
     * {@link AEKey#getFuzzySearchValue()} and {@link AEKey#getFuzzySearchMaxValue()}.
     * <p/>
     * For items this is used for damage-based search and filtering.
     */
    public final boolean supportsFuzzyRangeSearch() {
        return getType().supportsFuzzyRangeSearch();
    }

    public final ITextComponent getDisplayName() {
        var ret = cachedDisplayName;

        if (ret == null) {
            cachedDisplayName = ret = computeDisplayName();
        }

        return ret;
    }

    /**
     * Compute the display name, which is used to sort by name in client terminal. Will be cached by
     * {@link #getDisplayName()}.
     */
    protected abstract ITextComponent computeDisplayName();

    /**
     * Adds the drops if the container holding this key is broken, such as an interface holding stacks. Item stacks
     * should be placed in the list and not spawned directly into the world
     *
     * @param amount Amount to drop
     * @param drops  Drop list to append to, in case of {@link ItemStack} drops
     * @param level  World where the stacks were being held
     * @param pos    Position where the stacks were being held
     */
    public void addDrops(long amount, List<ItemStack> drops, World level, BlockPos pos) {
        GenericResourcePackageItem.addDrop(this, amount, drops);
    }

    public abstract boolean isTagged(String tag);

    @Nullable
    public abstract NBTBase get(String componentId);

    /**
     * @return true if this key has *any* components attached.
     */
    public abstract boolean hasComponents();
}
