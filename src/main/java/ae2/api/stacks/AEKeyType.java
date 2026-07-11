/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.stacks;

import ae2.api.storage.AEKeyFilter;
import ae2.util.ReadableNumberConverter;
import com.google.common.base.Preconditions;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.stream.Stream;

/**
 * Defines the properties of a specific subclass of {@link AEKey}. I.e. for {@link AEItemKey}, there is
 * {@link AEItemKeys}.
 */
public abstract class AEKeyType {
    private final ResourceLocation id;
    private final Class<? extends AEKey> keyClass;
    private final AEKeyFilter filter;
    private final ITextComponent description;

    public AEKeyType(ResourceLocation id, Class<? extends AEKey> keyClass, ITextComponent description) {
        Preconditions.checkArgument(!keyClass.equals(AEKey.class), "Can't register a key type for AEKey itself");
        this.id = id;
        this.keyClass = keyClass;
        this.filter = what -> what.getType() == this;
        this.description = description;
    }

    public static AEKeyType items() {
        return AEItemKeys.INSTANCE;
    }

    @Nullable
    public static AEKeyType fromRawId(int id) {
        return id >= 0 && id <= Byte.MAX_VALUE ? AEKeyTypesInternal.byId(id) : null;
    }

    public static AEKeyType fluids() {
        return AEFluidKeys.INSTANCE;
    }

    public final ResourceLocation getId() {
        return id;
    }

    public final Class<? extends AEKey> getKeyClass() {
        return keyClass;
    }

    public final byte getRawId() {
        var id = AEKeyTypesInternal.getId(this);
        if (id < 0 || id > 127) {
            throw new IllegalStateException("Key type " + this + " has an invalid numeric id: " + id);
        }
        return (byte) id;
    }

    public int getAmountPerOperation() {
        return 1;
    }

    public int getAmountPerByte() {
        return 8;
    }

    @Nullable
    public abstract AEKey readFromPacket(PacketBuffer input);

    @Nullable
    public abstract AEKey loadKeyFromTag(NBTTagCompound tag);

    @Nullable
    public final AEKey tryCast(AEKey key) {
        return keyClass.isInstance(key) ? keyClass.cast(key) : null;
    }

    public final boolean contains(AEKey key) {
        return keyClass.isInstance(key);
    }

    public boolean supportsFuzzyRangeSearch() {
        return false;
    }

    /**
     * Returns whether crafting CPUs may intercept inserted keys of this type as crafting output.
     */
    public boolean isCraftingCpuInsertable() {
        return true;
    }

    public final AEKeyFilter filter() {
        return filter;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public ITextComponent getDescription() {
        return description;
    }

    @Nullable
    public String getUnitSymbol() {
        return null;
    }

    public int getAmountPerUnit() {
        return 1;
    }

    public final String formatAmount(long amount, AmountFormat format) {
        return switch (format) {
            case FULL -> formatFullAmount(amount);
            case SLOT -> formatShortAmount(amount, 4);
            case SLOT_LARGE_FONT -> formatShortAmount(amount, 3);
        };
    }

    private String formatFullAmount(long amount) {
        var result = new StringBuilder();

        if (getAmountPerUnit() > 1) {
            var units = amount / (double) getAmountPerUnit();
            result.append(NumberFormat.getNumberInstance().format(units));
        } else {
            result.append(NumberFormat.getNumberInstance().format(amount));
        }

        var unit = getUnitSymbol();
        if (unit != null) {
            result.append(' ').append(unit);
        }

        return result.toString();
    }

    private String formatShortAmount(long amount, int maxWidth) {
        if (getAmountPerUnit() > 1) {
            var units = amount / (double) getAmountPerUnit();
            return ReadableNumberConverter.format(units, maxWidth);
        } else {
            return ReadableNumberConverter.format(amount, maxWidth);
        }
    }

    public Stream<String> getTagNames() {
        return Stream.empty();
    }
}
