package ae2.api.stacks;

import ae2.api.storage.AEKeyFilter;
import ae2.core.AELog;
import ae2.text.TextComponentItemStack;
import com.google.common.base.Preconditions;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class AEItemKey extends AEKey {
    @NotNull
    private final ItemStack stack;
    private final int hashCode;
    private final int maxStackSize;
    private final int damage;

    private AEItemKey(ItemStack stack) {
        Preconditions.checkArgument(!stack.isEmpty(), "stack is empty");
        this.stack = stack;
        this.stack.setCount(1);
        this.hashCode = hashStack(stack);
        this.maxStackSize = stack.getMaxStackSize();
        this.damage = stack.getItemDamage();
    }

    @Nullable
    public static AEItemKey of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        return new AEItemKey(stack.copy());
    }

    public static boolean matches(AEKey what, ItemStack itemStack) {
        return what instanceof AEItemKey itemKey && itemKey.matches(itemStack);
    }

    public static boolean is(AEKey what) {
        return what instanceof AEItemKey;
    }

    public static AEKeyFilter filter() {
        return AEItemKey::is;
    }

    public static AEItemKey of(Item item) {
        return of(new ItemStack(item));
    }

    @Nullable
    public static AEItemKey fromTag(NBTTagCompound tag) {
        try {
            var stack = new ItemStack(tag);
            return of(stack);
        } catch (Exception e) {
            AELog.debug("Tried to load an invalid item key from NBT: %s", tag, e);
            return null;
        }
    }

    public static AEItemKey fromPacket(PacketBuffer data) {
        try {
            return of(data.readItemStack());
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read item key", e);
        }
    }

    private static int hashStack(ItemStack stack) {
        int result = stack.getItem().hashCode();
        result = 31 * result + stack.getMetadata();
        result = 31 * result + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
        return result;
    }

    @Override
    public AEKeyType getType() {
        return AEKeyType.items();
    }

    @Override
    public AEItemKey dropSecondary() {
        return of(new ItemStack(stack.getItem()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AEItemKey aeItemKey = (AEItemKey) o;
        return this.hashCode == aeItemKey.hashCode && ItemStack.areItemStacksEqual(stack, aeItemKey.stack);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public boolean is(Item item) {
        return stack.getItem() == item;
    }

    public boolean matches(ItemStack stack) {
        var compare = stack.copy();
        compare.setCount(1);
        return !stack.isEmpty() && ItemStack.areItemStacksEqual(this.stack, compare);
    }

    public boolean matches(Ingredient ingredient) {
        return ingredient.apply(getReadOnlyStack());
    }

    @NotNull
    public ItemStack getReadOnlyStack() {
        return stack;
    }

    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }

        var result = stack.copy();
        result.setCount(count);
        return result;
    }

    public Item getItem() {
        return stack.getItem();
    }

    @Override
    public NBTTagCompound toTag() {
        var result = new NBTTagCompound();
        stack.writeToNBT(result);
        return result;
    }

    @Override
    public Object getPrimaryKey() {
        return stack.getItem();
    }

    @Override
    public int getFuzzySearchValue() {
        return this.damage;
    }

    @Override
    public int getFuzzySearchMaxValue() {
        return getReadOnlyStack().getMaxDamage();
    }

    @Override
    public ResourceLocation getId() {
        return Item.REGISTRY.getNameForObject(stack.getItem());
    }

    @Override
    public ItemStack wrapForDisplayOrFilter() {
        return toStack();
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, World level, BlockPos pos) {
        while (amount > 0) {
            if (drops.size() > 1000) {
                AELog.warn("Tried dropping an excessive amount of items, ignoring %s %ss", amount, stack.getItem());
                break;
            }

            var taken = Math.min(amount, getMaxStackSize());
            amount -= taken;
            drops.add(toStack((int) taken));
        }
    }

    @Override
    protected ITextComponent computeDisplayName() {
        return TextComponentItemStack.of(getReadOnlyStack());
    }

    @Override
    public boolean hasComponents() {
        var tag = stack.getTagCompound();
        return tag != null && !tag.isEmpty();
    }

    @Override
    public boolean isTagged(String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }

        for (var oreId : OreDictionary.getOreIDs(stack)) {
            if (OreDictionary.getOreName(oreId).equals(tag)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public @org.jspecify.annotations.Nullable NBTBase get(String componentId) {
        var tag = stack.getTagCompound();
        var value = tag == null ? null : tag.getTag(componentId);
        return value == null ? null : value.copy();
    }

    public boolean isDamaged() {
        return damage > 0;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    @Override
    public void writeToPacket(PacketBuffer data) {
        data.writeItemStack(stack);
    }

    @Override
    public String toString() {
        var id = Item.REGISTRY.getNameForObject(stack.getItem());
        String idString = id != null ? id.toString() : stack.getItem().getClass().getName() + "(unregistered)";
        return stack.hasTagCompound() ? idString + " (with tag)" : idString;
    }
}
