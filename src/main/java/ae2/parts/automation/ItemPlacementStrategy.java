package ae2.parts.automation;

import ae2.api.behaviors.PlacementStrategy;
import ae2.api.config.Actionable;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.util.AEPartLocation;
import ae2.core.AEConfig;
import ae2.util.Platform;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.IPlantable;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ItemPlacementStrategy implements PlacementStrategy {
    private static final int ENTITY_LIMIT_CHECK_RADIUS = 8;
    private final WorldServer level;
    private final BlockPos pos;
    private final EnumFacing side;
    private final TileEntity host;
    @Nullable
    private final UUID ownerUuid;
    private boolean blocked = false;

    public ItemPlacementStrategy(WorldServer level, BlockPos pos, EnumFacing side,
                                 TileEntity host, @Nullable UUID owningEntityPlayerId) {
        this.level = level;
        this.pos = pos;
        this.side = side;
        this.host = host;
        this.ownerUuid = owningEntityPlayerId;
    }

    private static void spawnItemEntity(World level, TileEntity te, EnumFacing side,
                                        ItemStack is) {
        final double centerX = te.getPos().getX() + .5;
        final double centerY = te.getPos().getY();
        final double centerZ = te.getPos().getZ() + .5;

        Entity entity = new EntityItem(level, centerX, centerY, centerZ, is.copy());

        final double additionalYOffset = side.getYOffset() == -1 ? 1 - entity.height : 0;
        final double spawnAreaHeight = Math.max(0, 1 - entity.height);
        final double spawnAreaWidth = Math.max(0, 1 - entity.width);

        final double offsetX = side.getXOffset() == 0
            ? level.rand.nextFloat() * spawnAreaWidth - spawnAreaWidth / 2
            : side.getXOffset() * (.525 + entity.width / 2);
        final double offsetY = side.getYOffset() == 0
            ? level.rand.nextFloat() * spawnAreaHeight
            : side.getYOffset() + additionalYOffset;
        final double offsetZ = side.getZOffset() == 0
            ? level.rand.nextFloat() * spawnAreaWidth - spawnAreaWidth / 2
            : side.getZOffset() * (.525 + entity.width / 2);

        entity.setPosition(centerX + offsetX, centerY + offsetY, centerZ + offsetZ);
        entity.motionX = side.getXOffset() * .1;
        entity.motionY = side.getYOffset() * 0.1;
        entity.motionZ = side.getZOffset() * 0.1;
        level.spawnEntity(entity);
    }

    public void clearBlocked() {
        this.blocked = !level.getBlockState(pos).getBlock().isReplaceable(level, pos);
    }

    public final long placeInWorld(AEKey what, long amount, Actionable type, boolean placeAsEntity) {
        if (this.blocked || !(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        ItemStack is = itemKey.toStack((int) Math.min(amount, itemKey.getMaxStackSize()));
        Item i = is.getItem();

        long maxStorage = Math.min(amount, is.getCount());
        boolean worked = false;
        EnumFacing side = this.side.getOpposite();
        BlockPos placePos = pos;

        if (level.getBlockState(placePos).getBlock().isReplaceable(level, placePos)) {
            if (placeAsEntity) {
                int sum = this.countEntitesAround(level, placePos);

                if (sum < AEConfig.instance().getFormationPlaneEntityLimit()) {
                    worked = true;

                    if (type == Actionable.MODULATE) {
                        is.setCount((int) maxStorage);
                        spawnItemEntity(level, host, side, is);
                    }
                }
            } else {
                EntityPlayer player = Platform.getFakeEntityPlayer(level, ownerUuid);
                Platform.configurePlayer(player, AEPartLocation.fromFacing(side), host);

                EnumHand hand = EnumHand.MAIN_HAND;
                player.setHeldItem(hand, is);

                maxStorage = is.getCount();
                worked = true;
                if (type == Actionable.MODULATE) {
                    if (i instanceof IPlantable || i instanceof ItemSkull || i == Item.getItemFromBlock(Blocks.REEDS)) {
                        boolean didWork = false;

                        if (side.getXOffset() == 0 && side.getZOffset() == 0) {
                            didWork = i.onItemUse(player, level, placePos.offset(side), hand,
                                side.getOpposite(), side.getXOffset(), side.getYOffset(), side.getZOffset())
                                == EnumActionResult.SUCCESS;
                        }

                        if (!didWork && side.getXOffset() == 0 && side.getZOffset() == 0) {
                            didWork = i.onItemUse(player, level, placePos.offset(side.getOpposite()), hand,
                                side, side.getXOffset(), side.getYOffset(), side.getZOffset())
                                == EnumActionResult.SUCCESS;
                        }

                        if (!didWork && side.getYOffset() == 0) {
                            didWork = i.onItemUse(player, level, placePos.offset(EnumFacing.DOWN), hand,
                                EnumFacing.UP, side.getXOffset(), side.getYOffset(), side.getZOffset())
                                == EnumActionResult.SUCCESS;
                        }

                        if (!didWork) {
                            i.onItemUse(player, level, placePos, hand, side.getOpposite(), side.getXOffset(),
                                side.getYOffset(), side.getZOffset());
                        }

                        maxStorage -= is.getCount();
                    } else {
                        i.onItemUse(player, level, placePos, hand, side.getOpposite(), side.getXOffset(),
                            side.getYOffset(), side.getZOffset());
                        maxStorage -= is.getCount();
                    }
                } else {
                    maxStorage = 1;
                }

                player.setHeldItem(hand, ItemStack.EMPTY);
            }
        }

        this.blocked = !level.getBlockState(placePos).getBlock().isReplaceable(level, placePos);

        if (worked) {
            return maxStorage;
        }

        return 0;
    }

    private int countEntitesAround(World level, BlockPos pos) {
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(ENTITY_LIMIT_CHECK_RADIUS);
        return level.getEntitiesWithinAABB(Entity.class, box).size();
    }
}
