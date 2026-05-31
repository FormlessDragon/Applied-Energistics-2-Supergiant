package ae2.items.tools;

import ae2.api.parts.IPart;
import ae2.api.parts.IPartItem;
import ae2.core.localization.InGameTooltip;
import ae2.items.AEBaseItem;
import ae2.parts.PartPlacement;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class MEPackageItem extends AEBaseItem {
    static final String IS_PART_TAG = "isPart";
    static final String ID_TAG = "id";
    static final String BLOCK_ID_TAG = "blockId";
    static final String STATE_TAG = "state";
    static final String DATA_TAG = "data";

    public MEPackageItem() {
        setMaxStackSize(1);
    }

    private static boolean restoreBlock(World world, BlockPos pos, NBTTagCompound tag) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(tag.getString(BLOCK_ID_TAG)));
        if (block == null || !world.mayPlace(block, pos, false, EnumFacing.UP, null)) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        IBlockState state = NBTUtil.readBlockState(tag.getCompoundTag(STATE_TAG));
        if (!world.setBlockState(pos, state, 3)) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile != null) {
            NBTTagCompound data = tag.getCompoundTag(DATA_TAG).copy();
            data.setInteger("x", pos.getX());
            data.setInteger("y", pos.getY());
            data.setInteger("z", pos.getZ());
            tile.readFromNBT(data);
            tile.markDirty();
            world.notifyBlockUpdate(pos, state, state, 3);
        }
        return true;
    }

    private static String getPackedDeviceName(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }

        if (tag.getBoolean(IS_PART_TAG)) {
            Item item = getItem(tag.getString(ID_TAG));
            if (item == null) {
                return null;
            }
            return new ItemStack(item).getDisplayName();
        }

        Block block = getBlock(tag.getString(BLOCK_ID_TAG));
        if (block == null) {
            return null;
        }
        return new ItemStack(block).getDisplayName();
    }

    private static Item getItem(String id) {
        ResourceLocation resourceLocation = getResourceLocation(id);
        return resourceLocation == null ? null : ForgeRegistries.ITEMS.getValue(resourceLocation);
    }

    private static Block getBlock(String id) {
        ResourceLocation resourceLocation = getResourceLocation(id);
        return resourceLocation == null ? null : ForgeRegistries.BLOCKS.getValue(resourceLocation);
    }

    private static ResourceLocation getResourceLocation(String id) {
        try {
            return new ResourceLocation(id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        String deviceName = getPackedDeviceName(stack);
        if (deviceName == null) {
            lines.add(TextFormatting.RED + InGameTooltip.PackagedDeviceInvalid.getLocal());
            return;
        }

        lines.add(TextFormatting.GRAY + InGameTooltip.PackagedDevice.getLocal(deviceName));
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return EnumActionResult.PASS;
        }
        if (tag.getBoolean(IS_PART_TAG)) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(tag.getString(ID_TAG)));
            if (!(item instanceof IPartItem<?>)) {
                return EnumActionResult.FAIL;
            }
            ItemStack partStack = new ItemStack(item);
            PartPlacement.Placement placement = PartPlacement.getPartPlacement(player, world, partStack, pos, side,
                new Vec3d(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ));
            if (placement == null) {
                return EnumActionResult.FAIL;
            }
            if (!world.isRemote) {
                IPart restoredPart = PartPlacement.placePart(player, world, partStack, placement.pos(), placement.side());
                if (restoredPart == null) {
                    return EnumActionResult.FAIL;
                }
                restoredPart.readFromNBT(tag.getCompoundTag(DATA_TAG).copy());
            }
            if (!world.isRemote && !player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            return EnumActionResult.SUCCESS;
        }
        boolean success = !tag.getBoolean(IS_PART_TAG) && restoreBlock(world, pos.offset(side), tag);
        if (!success) {
            return EnumActionResult.FAIL;
        }
        if (!world.isRemote && !player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }
}
