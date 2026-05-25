package appeng.api.stacks;

import appeng.core.definitions.AEItems;
import appeng.items.misc.WrappedGenericStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Represents some amount of some generic resource that AE can store or handle in crafting.
 */
public record GenericStack(AEKey what, long amount) {

    @ApiStatus.Internal
    public static final String AMOUNT_FIELD = "#";
    private static final String MISSING_CONTENT_ITEMSTACK_DATA = "missing_content_itemstack_data";
    private static final String MISSING_CONTENT_AEKEY_DATA = "missing_content_aekey_data";
    private static final String MISSING_CONTENT_ERROR = "missing_content_error";

    public GenericStack {
        Objects.requireNonNull(what, "what");
    }

    @Nullable
    public static GenericStack readBuffer(PacketBuffer buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }

        AEKey what = AEKey.readKey(buffer);
        if (what == null) {
            return null;
        }

        return new GenericStack(what, buffer.readVarLong());
    }

    public static void writeBuffer(@Nullable GenericStack stack, PacketBuffer buffer) {
        if (stack == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);

            AEKey.writeKey(buffer, stack.what);
            buffer.writeVarLong(stack.amount);
        }
    }

    @Nullable
    public static GenericStack readTag(NBTTagCompound tag) {
        if (tag.isEmpty()) {
            return null;
        }
        AEKey what = AEKey.fromTagGeneric(tag);
        if (what == null) {
            return createMissingContentStack(tag, "Failed to deserialize GenericStack");
        }
        return new GenericStack(what, tag.getLong(AMOUNT_FIELD));
    }

    public static GenericStack createMissingContentStack(NBTTagCompound originalData, String error) {
        ItemStack missingContent = AEItems.MISSING_CONTENT.stack();
        if (!missingContent.hasTagCompound()) {
            missingContent.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = missingContent.getTagCompound();
        if (tag == null) {
            throw new IllegalStateException("Missing content item stack lost its tag compound");
        }
        tag.setTag(MISSING_CONTENT_ITEMSTACK_DATA, originalData.copy());
        tag.setString(MISSING_CONTENT_ERROR, error);
        return new GenericStack(AEItemKey.of(missingContent), 1);
    }

    public static NBTTagCompound writeTag(@Nullable GenericStack stack) {
        if (stack == null) {
            return new NBTTagCompound();
        }

        if (stack.what() instanceof AEItemKey itemKey && AEItems.MISSING_CONTENT.is(itemKey)) {
            NBTBase originalKeyTag = itemKey.get(MISSING_CONTENT_AEKEY_DATA);
            if (originalKeyTag instanceof NBTTagCompound compound) {
                return compound.copy();
            }

            NBTBase originalItemTag = itemKey.get(MISSING_CONTENT_ITEMSTACK_DATA);
            if (originalItemTag instanceof NBTTagCompound compound) {
                return compound.copy();
            }
        }

        NBTTagCompound tag = stack.what().toTagGeneric();
        tag.setLong(AMOUNT_FIELD, stack.amount());
        return tag;
    }

    public static NBTTagList writeList(List<@Nullable GenericStack> stacks) {
        return GenericStackListCodec.encode(stacks);
    }

    public static List<@Nullable GenericStack> readList(NBTTagList tag) {
        return GenericStackListCodec.decode(tag);
    }

    @Nullable
    public static GenericStack fromItemStack(ItemStack stack) {
        GenericStack genericStack = GenericStack.unwrapItemStack(stack);
        if (genericStack != null) {
            return genericStack;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return null;
        }
        return new GenericStack(key, stack.getCount());
    }

    @Nullable
    public static GenericStack fromFluidStack(FluidStack stack) {
        AEFluidKey key = AEFluidKey.of(stack);
        if (key == null) {
            return null;
        }
        return new GenericStack(key, stack.amount);
    }

    public static long getStackSizeOrZero(@Nullable GenericStack stack) {
        return stack == null ? 0 : stack.amount;
    }

    public static ItemStack wrapInItemStack(@Nullable GenericStack stack) {
        if (stack != null) {
            return wrapInItemStack(stack.what(), stack.amount());
        } else {
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack wrapInItemStack(AEKey what, long amount) {
        return WrappedGenericStack.wrap(what, amount);
    }

    public static boolean isWrapped(ItemStack stack) {
        return stack.getItem() instanceof WrappedGenericStack;
    }

    public static @org.jspecify.annotations.Nullable GenericStack unwrapItemStack(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof WrappedGenericStack item) {
            AEKey what = item.unwrapWhat(stack);
            if (what != null) {
                long amount = item.unwrapAmount(stack);
                return new GenericStack(what, amount);
            }
        }

        return null;
    }

    public static GenericStack sum(GenericStack left, GenericStack right) {
        if (!left.what.equals(right.what)) {
            throw new IllegalArgumentException("Cannot sum generic stacks of " + left.what + " and " + right.what);
        }
        return new GenericStack(left.what, left.amount + right.amount);
    }
}
