/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.items.tools.powered;

import ae2.api.config.Actionable;
import ae2.api.config.FuzzyMode;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.StorageCells;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.upgrades.Upgrades;
import ae2.api.util.AEColor;
import ae2.api.util.DimensionalBlockPos;
import ae2.core.AEConfig;
import ae2.core.AppEng;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEDamageTypes;
import ae2.core.definitions.AEItems;
import ae2.core.localization.PlayerMessages;
import ae2.core.network.clientbound.MatterCannonPacket;
import ae2.items.contents.CellConfig;
import ae2.items.misc.PaintBallItem;
import ae2.items.tools.powered.powersink.AEBasePoweredItem;
import ae2.me.helpers.PlayerSource;
import ae2.recipes.mattercannon.MatterCannonAmmo;
import ae2.tile.misc.TilePaint;
import ae2.util.ConfigInventory;
import ae2.util.InteractionUtil;
import ae2.util.LookDirection;
import ae2.util.Platform;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.List;
import java.util.Set;

public class MatterCannonItem extends AEBasePoweredItem implements IBasicCellItem {
    private static final String STORAGE_CELL_FUZZY_MODE = "storage_cell_fuzzy_mode";
    private static final int ENERGY_PER_SHOT = 1600;

    public MatterCannonItem() {
        super(getBatteryCapacity());
    }

    public static int getDamageFromPenetration(float penetration) {
        return (int) Math.ceil(penetration / 20.0f);
    }

    private static double getBatteryCapacity() {
        try {
            return AEConfig.instance().getMatterCannonBattery();
        } catch (IllegalStateException ignored) {
            return 200000;
        }
    }

    private static boolean isMatterCannonBlockDamageEnabled() {
        try {
            return AEConfig.instance().isMatterCanonBlockDamageEnabled();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                         final ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        addCellInformationToTooltip(stack, lines);
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 800d + 800d * Upgrades.getEnergyCardMultiplier(getUpgrades(stack));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World level, EntityPlayer player, EnumHand hand) {
        var stack = player.getHeldItem(hand);
        var direction = InteractionUtil.getPlayerRay(player, 255);

        if (fireCannon(level, stack, player, hand, direction)) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        } else {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
    }

    public boolean fireCannon(World level, ItemStack stack, EntityPlayer player, EnumHand hand, LookDirection dir) {
        var inv = StorageCells.getCellInventory(stack, null);
        if (inv == null) {
            return false;
        }

        var itemList = inv.getAvailableStacks();
        var req = itemList.getFirstEntry(AEItemKey.class);
        if (req == null || !(req.getKey() instanceof AEItemKey itemKey)) {
            if (!level.isRemote) {
                player.sendStatusMessage(PlayerMessages.AmmoDepleted.text(), true);
            }
            return true;
        }

        int shotPower = 1;
        var upgrades = getUpgrades(stack);
        if (upgrades != null) {
            shotPower += upgrades.getInstalledUpgrades(AEItems.SPEED_CARD.item());
        }
        shotPower = Math.min(shotPower, (int) req.getLongValue());

        if (getAECurrentPower(stack) < ENERGY_PER_SHOT) {
            return false;
        }

        shotPower = Math.min(shotPower, (int) getAECurrentPower(stack) / ENERGY_PER_SHOT);
        extractAEPower(stack, ENERGY_PER_SHOT * shotPower, Actionable.MODULATE);

        if (level.isRemote) {
            return true;
        }

        var aeAmmo = inv.extract(req.getKey(), 1, Actionable.MODULATE, new PlayerSource(player));
        if (aeAmmo == 0) {
            return true;
        }

        var rayFrom = dir.a();
        var rayTo = dir.b();
        var direction = rayTo.subtract(rayFrom);
        direction = direction.normalize();

        var x = rayFrom.x;
        var y = rayFrom.y;
        var z = rayFrom.z;

        var penetration = getPenetration(itemKey) * shotPower;
        if (penetration <= 0) {
            if (itemKey.getItem() instanceof PaintBallItem paintBallItem) {
                shootPaintBalls(paintBallItem.getColor(), paintBallItem.isLumen(), level, player, hand, rayFrom, rayTo,
                    direction, x, y, z);
                return true;
            }
        } else {
            standardAmmo(penetration, level, player, rayFrom, rayTo, direction, x, y, z);
        }

        return true;
    }

    private void shootPaintBalls(AEColor color, boolean lit, World level, EntityPlayer player, EnumHand hand, Vec3d from, Vec3d to,
                                 Vec3d direction, double x, double y, double z) {
        final AxisAlignedBB bb = new AxisAlignedBB(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
            Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).grow(16, 16, 16);

        Entity entity = null;
        Vec3d entityIntersection = null;
        final List<Entity> list = level.getEntitiesWithinAABBExcludingEntity(player, bb);
        double closest = 9999999.0D;

        for (Entity entity1 : list) {
            if (entity1 instanceof EntityItem || !entity1.isEntityAlive() || entity1.isRidingOrBeingRiddenBy(player)) {
                continue;
            }

            final AxisAlignedBB boundingBox = entity1.getEntityBoundingBox().grow(0.3F, 0.3F, 0.3F);
            final RayTraceResult intersection = boundingBox.calculateIntercept(from, to);

            if (intersection != null) {
                final double nd = from.squareDistanceTo(intersection.hitVec);
                if (nd < closest) {
                    entity = entity1;
                    entityIntersection = intersection.hitVec;
                    closest = nd;
                }
            }
        }

        RayTraceResult pos = level.rayTraceBlocks(from, to, false);
        final Vec3d start = new Vec3d(x, y, z);
        if (entity != null && pos != null && pos.typeOfHit != Type.MISS && pos.hitVec.squareDistanceTo(start) > closest) {
            pos = new RayTraceResult(entity, entityIntersection);
        } else if (entity != null && (pos == null || pos.typeOfHit == Type.MISS)) {
            pos = new RayTraceResult(entity, entityIntersection);
        }

        AppEng.instance().sendToAllNearExcept(null, x, y, z, 256, level,
            new MatterCannonPacket(x, y, z, direction.x, direction.y, direction.z,
                (byte) (pos == null || pos.typeOfHit == Type.MISS ? 32 : pos.hitVec.squareDistanceTo(start) + 1)));

        if (pos == null || pos.typeOfHit == Type.MISS) {
            return;
        }

        if (pos.typeOfHit == Type.ENTITY) {
            var entityHit = pos.entityHit;
            if (entityHit instanceof EntitySheep sheep) {
                sheep.setFleeceColor(color.dye);
            }
            entityHit.attackEntityFrom(DamageSource.causePlayerDamage(player), 0);
            return;
        }

        final EnumFacing side = pos.sideHit;
        final BlockPos hitPos = pos.getBlockPos().offset(side);

        if (!Platform.hasPermissions(new DimensionalBlockPos(level, hitPos), player)) {
            return;
        }

        if (ForgeEventFactory.onPlayerBlockPlace(player, BlockSnapshot.getBlockSnapshot(level, hitPos), side,
            hand).isCanceled()) {
            return;
        }

        final IBlockState whatsThere = level.getBlockState(hitPos);
        if (whatsThere.getBlock().isReplaceable(level, hitPos) && level.isAirBlock(hitPos)) {
            level.setBlockState(hitPos, AEBlocks.PAINT.block().getDefaultState(), 3);
        }

        final var te = level.getTileEntity(hitPos);
        if (te instanceof TilePaint paint) {
            final Vec3d hp = pos.hitVec.subtract(hitPos.getX(), hitPos.getY(), hitPos.getZ());
            paint.addBlot(color, lit, side.getOpposite(), hp);
        }
    }

    private void standardAmmo(float penetration, World level, EntityPlayer player, Vec3d from, Vec3d to,
                              Vec3d direction, double x, double y, double z) {
        boolean hasDestroyed = true;
        while (penetration > 0 && hasDestroyed) {
            hasDestroyed = false;

            final AxisAlignedBB bb = new AxisAlignedBB(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
                Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).grow(16, 16, 16);

            Entity entity = null;
            Vec3d entityIntersection = null;
            final List<Entity> list = level.getEntitiesWithinAABBExcludingEntity(player, bb);
            double closest = 9999999.0D;

            for (Entity entity1 : list) {
                if (entity1 instanceof EntityItem || !entity1.isEntityAlive() || entity1.isRidingOrBeingRiddenBy(player)) {
                    continue;
                }

                final AxisAlignedBB boundingBox = entity1.getEntityBoundingBox().grow(0.3F, 0.3F, 0.3F);
                final RayTraceResult intersection = boundingBox.calculateIntercept(from, to);
                if (intersection != null) {
                    final double nd = from.squareDistanceTo(intersection.hitVec);
                    if (nd < closest) {
                        entity = entity1;
                        entityIntersection = intersection.hitVec;
                        closest = nd;
                    }
                }
            }

            final Vec3d start = new Vec3d(x, y, z);
            RayTraceResult pos = level.rayTraceBlocks(from, to, true);
            if (entity != null && pos != null && pos.typeOfHit != Type.MISS && pos.hitVec.squareDistanceTo(start) > closest) {
                pos = new RayTraceResult(entity, entityIntersection);
            } else if (entity != null && (pos == null || pos.typeOfHit == Type.MISS)) {
                pos = new RayTraceResult(entity, entityIntersection);
            }

            AppEng.instance().sendToAllNearExcept(null, x, y, z, 256, level,
                new MatterCannonPacket(x, y, z, direction.x, direction.y, direction.z,
                    (byte) (pos == null || pos.typeOfHit == Type.MISS ? 32 : pos.hitVec.squareDistanceTo(start) + 1)));

            if (pos == null || pos.typeOfHit == Type.MISS) {
                continue;
            }

            final DamageSource dmgSrc = AEDamageTypes.causeMatterCannonDamage(player);
            if (pos.typeOfHit == Type.ENTITY) {
                final int dmg = getDamageFromPenetration(penetration);
                Entity entityHit = pos.entityHit;
                if (entityHit instanceof EntityLivingBase living) {
                    penetration -= dmg;
                    if (living.attackEntityFrom(dmgSrc, dmg)) {
                        living.knockBack(player, 0, -direction.x, -direction.z);
                        if (!living.isEntityAlive()) {
                            hasDestroyed = true;
                        }
                    }
                } else if (entityHit instanceof EntityItem) {
                    hasDestroyed = true;
                    entityHit.setDead();
                } else if (entityHit.attackEntityFrom(dmgSrc, dmg)) {
                    hasDestroyed = !entityHit.isDead;
                }
            } else if (pos.typeOfHit == Type.BLOCK) {
                if (!isMatterCannonBlockDamageEnabled()) {
                    penetration = 0;
                } else {
                    final BlockPos blockPos = pos.getBlockPos();
                    final IBlockState bs = level.getBlockState(blockPos);
                    final float hardness = bs.getBlockHardness(level, blockPos) * 9.0f;
                    if (hardness >= 0.0F && penetration > hardness
                        && Platform.hasPermissions(new DimensionalBlockPos(level, blockPos), player)) {
                        hasDestroyed = true;
                        penetration -= hardness;
                        penetration *= 0.60F;
                        level.destroyBlock(blockPos, true);
                    }
                }
            }
        }
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack is) {
        return UpgradeInventories.forItem(is, 4, this::onUpgradesChanged);
    }

    private void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        setAEMaxPower(stack, getBatteryCapacity() * (1 + Upgrades.getEnergyCardMultiplier(upgrades) * 8));
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(Set.of(AEKeyType.items()), is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        var tag = is.getTagCompound();
        if (tag != null && tag.hasKey(STORAGE_CELL_FUZZY_MODE, 8)) {
            try {
                return FuzzyMode.valueOf(tag.getString(STORAGE_CELL_FUZZY_MODE));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            is.setTagCompound(tag);
        }
        tag.setString(STORAGE_CELL_FUZZY_MODE, fzMode.name());
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return 512;
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return 8;
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return 1;
    }

    @Override
    public boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {
        if (requestedAddition instanceof AEItemKey itemKey) {
            var pen = getPenetration(itemKey);
            if (pen > 0) {
                return false;
            }
            return !(itemKey.getItem() instanceof PaintBallItem);
        }
        return true;
    }

    @Override
    public double getIdleDrain() {
        return 0.5;
    }

    @Override
    public boolean storableInStorageCell() {
        return true;
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    private float getPenetration(AEItemKey what) {
        return getPenetration(what.toStack());
    }

    private float getPenetration(ItemStack what) {
        var ammo = MatterCannonAmmo.findAmmo(what);
        return ammo != null ? ammo.weight() : 0;
    }
}
