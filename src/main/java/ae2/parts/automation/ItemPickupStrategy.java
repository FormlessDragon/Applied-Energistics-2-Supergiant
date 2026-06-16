package ae2.parts.automation;

import ae2.api.behaviors.PickupSink;
import ae2.api.behaviors.PickupStrategy;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.stacks.AEItemKey;
import ae2.core.AppEng;
import ae2.core.network.clientbound.BlockTransitionEffectPacket;
import ae2.core.network.clientbound.ItemTransitionEffectPacket;
import ae2.items.misc.GenericResourcePackageItem;
import ae2.util.Platform;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ItemPickupStrategy implements PickupStrategy {

    private final WorldServer level;
    private final BlockPos pos;
    private final EnumFacing side;
    private final Object2IntMap<Enchantment> enchantments;
    @Nullable
    private final UUID ownerUuid;

    private boolean isAccepting = true;

    public ItemPickupStrategy(WorldServer level, BlockPos pos, EnumFacing side, TileEntity host,
                              Object2IntMap<Enchantment> enchantments, @Nullable UUID owningEntityPlayerId) {
        this.level = level;
        this.pos = pos;
        this.side = side;
        this.enchantments = enchantments;
        this.ownerUuid = owningEntityPlayerId;
    }

    public static boolean isBlockBlacklisted(Block block) {
        return false;
    }

    public static boolean isItemBlacklisted(Item item) {
        return false;
    }

    @Override
    public void reset() {
        this.isAccepting = true;
    }

    @Override
    public boolean canPickUpEntity(Entity entity) {
        return entity instanceof EntityItem;
    }

    @Override
    public boolean pickUpEntity(IEnergySource energySource, PickupSink sink, Entity entity) {
        if (!this.isAccepting || !(entity instanceof EntityItem entityItem)) {
            return false;
        }

        if (isItemBlacklisted(entityItem.getItem().getItem())) {
            return false;
        }

        boolean changed = this.storeEntityItem(sink, entityItem);

        if (changed) {
            AppEng.instance().sendToAllNearExcept(null, pos.getX(), pos.getY(), pos.getZ(), 64,
                level, new ItemTransitionEffectPacket(entity.posX, entity.posY, entity.posZ, side));
        }

        return true;
    }

    @Override
    public Result tryPickup(IEnergySource energySource, PickupSink sink) {
        if (this.isAccepting) {
            IBlockState blockState = level.getBlockState(pos);
            if (this.canHandleBlock(level, pos, blockState)) {
                List<ItemStack> items = this.obtainBlockDrops(level, pos);
                float requiredPower = this.calculateEnergyUsage(level, pos, items);

                boolean hasPower = energySource.extractAEPower(requiredPower, Actionable.SIMULATE,
                    PowerMultiplier.CONFIG) > requiredPower - 0.1F;
                boolean canStore = this.canStoreItemStacks(sink, items);

                if (hasPower && canStore) {
                    this.completePickup(energySource, sink, items, requiredPower, blockState);
                    return Result.PICKED_UP;
                } else {
                    return Result.CANT_STORE;
                }
            }
        }

        return Result.CANT_PICKUP;
    }

    private void completePickup(IEnergySource energySource, PickupSink sink, List<ItemStack> items, float requiredPower,
                                IBlockState blockState) {
        if (!this.breakBlockAndStoreExtraItems(sink, level, pos)) {
            return;
        }

        for (ItemStack item : items) {
            int inserted = storeItemStack(sink, item);
            if (inserted < item.getCount()) {
                item = item.copy();
                item.shrink(inserted);
                Platform.spawnDrops(level, pos, Collections.singletonList(item));
            }
        }

        energySource.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);

        AppEng.instance().sendToAllNearExcept(null, pos.getX(), pos.getY(), pos.getZ(), 64, level,
            new BlockTransitionEffectPacket(pos, blockState, side, BlockTransitionEffectPacket.SoundMode.NONE));
    }

    private boolean storeEntityItem(PickupSink sink, EntityItem entityItem) {
        if (!entityItem.isDead) {
            int inserted = this.storeItemStack(sink, entityItem.getItem());
            return this.handleOverflow(entityItem, inserted);
        }
        return false;
    }

    private int storeItemStack(PickupSink sink, ItemStack item) {
        if (item.isEmpty()) {
            return 0;
        }

        if (GenericResourcePackageItem.isPackage(item)) {
            var resource = GenericResourcePackageItem.unwrap(item);
            if (resource == null) {
                return 0;
            }
            long inserted = sink.insert(resource.what(), resource.amount(), Actionable.MODULATE);
            ItemStack remainder = inserted >= resource.amount()
                ? ItemStack.EMPTY
                : GenericResourcePackageItem.wrap(resource.what(), resource.amount() - inserted);
            item.setTagCompound(remainder.isEmpty() ? null : remainder.getTagCompound());
            this.isAccepting = remainder.isEmpty();
            return remainder.isEmpty() ? item.getCount() : 0;
        }

        var what = AEItemKey.of(item);
        long inserted = sink.insert(what, item.getCount(), Actionable.MODULATE);
        this.isAccepting = inserted >= item.getCount();
        return (int) inserted;
    }

    private boolean handleOverflow(EntityItem entityItem, int inserted) {
        int entityItemCount = entityItem.getItem().getCount();
        if (inserted >= entityItemCount) {
            entityItem.setDead();
            return true;
        }

        int newStackSize = entityItemCount - inserted;
        boolean changed = entityItemCount != newStackSize;
        entityItem.getItem().setCount(newStackSize);
        return changed;
    }


    private boolean canHandleBlock(WorldServer level, BlockPos pos, IBlockState state) {
        Material material = state.getMaterial();
        float hardness = state.getBlockHardness(level, pos);
        boolean ignoreMaterials = material == Material.AIR || material == Material.LAVA || material == Material.WATER
            || material.isLiquid();
        boolean ignoreBlocks = isBlockBlacklisted(state.getBlock());

        FakePlayer fakePlayer = Platform.getFakeEntityPlayer(level, ownerUuid);

        return !ignoreMaterials && !ignoreBlocks && hardness >= 0F && !level.isAirBlock(pos) && level.isBlockLoaded(pos)
            && level.canMineBlockBody(fakePlayer, pos);
    }

    protected List<ItemStack> obtainBlockDrops(WorldServer level, BlockPos pos) {
        FakePlayer fakePlayer = Platform.getFakeEntityPlayer(level, ownerUuid);
        IBlockState state = level.getBlockState(pos);

        if (state.getBlock().canSilkHarvest(level, pos, state, fakePlayer)
            && enchantments.containsKey(Enchantments.SILK_TOUCH)) {
            Item item = Item.getItemFromBlock(state.getBlock());
            if (item == Items.AIR) {
                return Collections.emptyList();
            }

            int meta = item.getHasSubtypes() ? state.getBlock().getMetaFromState(state) : 0;
            return Collections.singletonList(new ItemStack(item, 1, meta));
        }

        NonNullList<ItemStack> drops = NonNullList.create();
        int fortune = enchantments.getInt(Enchantments.FORTUNE);
        state.getBlock().getDrops(drops, level, pos, state, fortune);
        drops.removeIf(ItemStack::isEmpty);
        return drops;
    }

    protected float calculateEnergyUsage(WorldServer level, BlockPos pos, List<ItemStack> items) {
        boolean useEnergy = true;

        IBlockState state = level.getBlockState(pos);
        float hardness = state.getBlockHardness(level, pos);

        float requiredEnergy = 1 + hardness;
        for (ItemStack is : items) {
            requiredEnergy += is.getCount();
        }

        if (!enchantments.isEmpty()) {
            float efficiencyFactor = 1F;
            int efficiencyLevel = enchantments.getInt(Enchantments.EFFICIENCY);
            if (efficiencyLevel > 0) {
                efficiencyFactor *= (float) Math.pow(0.85, efficiencyLevel);
            }

            int unbreakingLevel = enchantments.getInt(Enchantments.UNBREAKING);
            if (unbreakingLevel > 0) {
                int randomNumber = ThreadLocalRandom.current().nextInt(unbreakingLevel + 1);
                useEnergy = randomNumber == 0;
            }

            int levelSum = 0;
            for (Object2IntMap.Entry<Enchantment> entry : enchantments.object2IntEntrySet()) {
                Enchantment enchantment = entry.getKey();
                if (enchantment != Enchantments.EFFICIENCY && enchantment != Enchantments.UNBREAKING) {
                    levelSum += entry.getIntValue();
                }
            }
            requiredEnergy *= (levelSum > 0 ? 8 * levelSum : 1) * efficiencyFactor;
        }

        return useEnergy ? requiredEnergy : 0;
    }

    private boolean canStoreItemStacks(PickupSink sink, List<ItemStack> itemStacks) {
        boolean canStore = true;

        for (ItemStack itemStack : itemStacks) {
            var itemToTest = AEItemKey.of(itemStack);
            long inserted = sink.insert(itemToTest, itemStack.getCount(), Actionable.SIMULATE);
            if (inserted < itemStack.getCount()) {
                canStore = false;
                break;
            }
        }

        this.isAccepting = canStore;
        return canStore;
    }

    private boolean breakBlockAndStoreExtraItems(PickupSink sink, WorldServer level, BlockPos pos) {
        if (!level.destroyBlock(pos, false)) {
            return false;
        }

        AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.2);
        for (EntityItem itemEntity : level.getEntitiesWithinAABB(EntityItem.class, box)) {
            this.storeEntityItem(sink, itemEntity);
        }
        return true;
    }

}
