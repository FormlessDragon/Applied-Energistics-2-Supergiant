package ae2.items.misc;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKeyTypesInternal;
import ae2.api.stacks.GenericStack;
import ae2.items.AEBaseItem;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MissingContentItem extends AEBaseItem {
    private static final String MISSING_CONTENT_ITEMSTACK_DATA = "missing_content_itemstack_data";
    private static final String MISSING_CONTENT_AEKEY_DATA = "missing_content_aekey_data";
    private static final String MISSING_CONTENT_ERROR = "missing_content_error";

    public MissingContentItem() {
        super();
    }

    @Nullable
    public BrokenStackInfo getBrokenStackInfo(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            return null;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }

        NBTTagCompound itemStackData = tag.hasKey(MISSING_CONTENT_ITEMSTACK_DATA, Constants.NBT.TAG_COMPOUND)
            ? tag.getCompoundTag(MISSING_CONTENT_ITEMSTACK_DATA)
            : null;
        NBTTagCompound genericStackData = tag.hasKey(MISSING_CONTENT_AEKEY_DATA, Constants.NBT.TAG_COMPOUND)
            ? tag.getCompoundTag(MISSING_CONTENT_AEKEY_DATA)
            : null;

        if (itemStackData != null
            && itemStackData.hasKey("id", Constants.NBT.TAG_STRING)) {
            String missingId = itemStackData.getString("id");
            long amount = Math.max(1, itemStackData.getByte("Count") & 255);
            return new BrokenStackInfo(new TextComponentString(missingId), AEKeyType.items(), amount);
        }

        if (genericStackData != null) {
            String missingName = null;
            if (genericStackData.hasKey("id", Constants.NBT.TAG_STRING)) {
                missingName = genericStackData.getString("id");
            } else if (genericStackData.hasKey("FluidName", Constants.NBT.TAG_STRING)) {
                missingName = genericStackData.getString("FluidName");
            }

            if (missingName != null) {
                ITextComponent missingId = new TextComponentString(missingName);
                AEKeyType keyType = null;

                try {
                    String keyTypeString = genericStackData.getString(AEKey.TYPE_FIELD);
                    if (!keyTypeString.isEmpty()) {
                        ResourceLocation keyTypeId = new ResourceLocation(keyTypeString);
                        keyType = AEKeyTypesInternal.get(keyTypeId);
                        if (keyType == null) {
                            missingId = new TextComponentString(missingId.getFormattedText() + " (" + keyTypeString + ")");
                        }
                    }
                } catch (RuntimeException ignored) {
                }

                long amount = Math.max(1, genericStackData.getLong(GenericStack.AMOUNT_FIELD));
                return new BrokenStackInfo(missingId, keyType, amount);
            }
        }

        return null;
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(MISSING_CONTENT_ERROR, Constants.NBT.TAG_STRING)) {
            lines.add(TextFormatting.GRAY + tag.getString(MISSING_CONTENT_ERROR));
        }
    }

    public record BrokenStackInfo(ITextComponent displayName, @Nullable AEKeyType keyType, long amount) {
    }
}
