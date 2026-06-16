package ae2.items.misc;

import ae2.api.config.Actionable;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageHelper;
import ae2.core.AELog;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.entity.EntityGenericResourcePackage;
import ae2.items.AEBaseItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class GenericResourcePackageItem extends AEBaseItem implements GenericStackHolderItem {
    private static final String RESOURCE_STACK = "resource_stack";

    public GenericResourcePackageItem() {
        setMaxStackSize(1);
    }

    public static ItemStack wrap(AEKey what, long amount) {
        Objects.requireNonNull(what, "what");
        if (amount <= 0) {
            AELog.warn("Tried to create a generic resource package with non-positive amount %d for %s", amount, what);
            return ItemStack.EMPTY;
        }

        var item = AEItems.GENERIC_RESOURCE_PACKAGE.asItem();
        var result = new ItemStack(item);
        result.setTagInfo(RESOURCE_STACK, GenericStack.writeTag(new GenericStack(what, amount)));
        return result;
    }

    public static boolean isPackage(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof GenericResourcePackageItem;
    }

    @Nullable
    public static GenericStack unwrap(ItemStack stack) {
        if (!isPackage(stack)) {
            return null;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(RESOURCE_STACK, 10)) {
            return null;
        }

        GenericStack resource = GenericStack.readTag(tag.getCompoundTag(RESOURCE_STACK));
        if (resource == null) {
            return null;
        }
        if (resource.amount() <= 0) {
            AELog.warn("Ignored generic resource package with non-positive amount %d for %s",
                resource.amount(), resource.what());
            return null;
        }
        return resource;
    }

    public static PackageInsertResult tryInsertPackage(ItemStack packageStack, IEnergySource energy,
                                                       MEStorage storage, IActionSource source, Actionable mode) {
        GenericStack resource = unwrap(packageStack);
        if (resource == null) {
            return new PackageInsertResult(packageStack, 0);
        }

        long inserted = StorageHelper.poweredInsert(energy, storage, resource.what(), resource.amount(), source, mode);
        if (inserted <= 0) {
            return new PackageInsertResult(packageStack, 0);
        }
        if (mode == Actionable.SIMULATE) {
            return new PackageInsertResult(packageStack, inserted);
        }
        if (inserted >= resource.amount()) {
            return new PackageInsertResult(ItemStack.EMPTY, inserted);
        }
        return new PackageInsertResult(wrap(resource.what(), resource.amount() - inserted), inserted);
    }

    public static void addDrop(AEKey what, long amount, List<ItemStack> drops) {
        ItemStack packageStack = wrap(what, amount);
        if (!packageStack.isEmpty()) {
            drops.add(packageStack);
        }
    }

    @Nullable
    @Override
    public GenericStack getGenericStack(ItemStack stack) {
        return unwrap(stack);
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Override
    public Entity createEntity(World world, Entity location, ItemStack itemstack) {
        EntityGenericResourcePackage entity = new EntityGenericResourcePackage(
            world, location.posX, location.posY, location.posZ, itemstack);
        entity.motionX = location.motionX;
        entity.motionY = location.motionY;
        entity.motionZ = location.motionZ;
        entity.setDefaultPickupDelay();
        return entity;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        GenericStack resource = unwrap(stack);
        if (resource == null) {
            return super.getItemStackDisplayName(stack);
        }

        ITextComponent displayName = resource.what().getDisplayName();
        return I18n.format("item.ae2.generic_resource_package.encapsulated", displayName.getFormattedText());
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        GenericStack resource = unwrap(stack);
        if (resource != null) {
            lines.add(GuiText.Amount.getLocal() + "："
                + resource.what().formatAmount(resource.amount(), AmountFormat.FULL));
        }
    }
}
