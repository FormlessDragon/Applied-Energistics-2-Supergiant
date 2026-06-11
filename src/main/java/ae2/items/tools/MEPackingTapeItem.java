package ae2.items.tools;

import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.AEParts;
import ae2.items.AEBaseItem;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
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
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.Set;

public class MEPackingTapeItem extends AEBaseItem {
    static final Set<ResourceLocation> PART_WHITELIST = Set.of(
        AEParts.INTERFACE.id(),
        AEParts.PATTERN_PROVIDER.id()
    );
    static final Set<ResourceLocation> BLOCK_WHITELIST = Set.of(
        AEBlocks.INTERFACE.id(),
        AEBlocks.PATTERN_PROVIDER.id(),
        AEBlocks.DRIVE.id()
    );

    public MEPackingTapeItem() {
        setMaxStackSize(1);
        setMaxDamage(64);
    }

    private static ItemStack packPart(World world, IPartHost host, Vec3d localHit) {
        var selected = host.selectPartLocal(localHit);
        IPart part = selected.part;
        if (part == null) {
            return ItemStack.EMPTY;
        }
        Item partItem = part.getPartItem().asItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(partItem);
        if (id == null || !PART_WHITELIST.contains(id)) {
            return ItemStack.EMPTY;
        }
        NBTTagCompound packageTag = new NBTTagCompound();
        packageTag.setBoolean(MEPackageItem.IS_PART_TAG, true);
        packageTag.setString(MEPackageItem.ID_TAG, id.toString());
        NBTTagCompound partTag = new NBTTagCompound();
        part.writeToNBT(partTag);
        packageTag.setTag(MEPackageItem.DATA_TAG, partTag);
        if (!world.isRemote && !host.removePart(part)) {
            return ItemStack.EMPTY;
        }
        ItemStack result = AEItems.PACKAGE.stack();
        result.setTagCompound(packageTag);
        return result;
    }

    private static ItemStack packBlock(World world, BlockPos pos, TileEntity tile) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        if (blockId == null || !BLOCK_WHITELIST.contains(blockId)) {
            return ItemStack.EMPTY;
        }
        NBTTagCompound packageTag = new NBTTagCompound();
        packageTag.setBoolean(MEPackageItem.IS_PART_TAG, false);
        packageTag.setString(MEPackageItem.BLOCK_ID_TAG, blockId.toString());
        packageTag.setTag(MEPackageItem.STATE_TAG, NBTUtil.writeBlockState(new NBTTagCompound(), state));
        packageTag.setTag(MEPackageItem.DATA_TAG, tile.writeToNBT(new NBTTagCompound()));
        if (!world.isRemote) {
            world.removeTileEntity(pos);
            world.setBlockToAir(pos);
        }
        ItemStack result = AEItems.PACKAGE.stack();
        result.setTagCompound(packageTag);
        return result;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (!player.isSneaking()) {
            return EnumActionResult.PASS;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) {
            return EnumActionResult.PASS;
        }
        ItemStack packageStack = ItemStack.EMPTY;
        if (tile instanceof IPartHost partHost) {
            packageStack = packPart(world, partHost, new Vec3d(hitX, hitY, hitZ));
        }
        if (packageStack.isEmpty()) {
            packageStack = packBlock(world, pos, tile);
        }
        if (packageStack.isEmpty()) {
            return EnumActionResult.PASS;
        }
        if (!world.isRemote) {
            if (!player.addItemStackToInventory(packageStack)) {
                player.dropItem(packageStack, false);
            }
            player.getHeldItem(hand).damageItem(1, player);
        }
        return EnumActionResult.SUCCESS;
    }
}
