package ae2.core.gui;

import ae2.api.parts.IPartHost;
import ae2.container.GuiIds;
import ae2.core.AppEngBase;
import ae2.core.gui.locator.BaublesItemLocator;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.parts.AEBasePart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class GuiOpener {

    private GuiOpener() {
    }

    public static void openGui(EntityPlayer player, GuiIds.GuiKey bridge, TileEntity tile) {
        openGui(player, bridge, tile, false);
    }

    public static void openGui(EntityPlayer player, GuiIds.GuiKey bridge, TileEntity tile, boolean returnedFromSubScreen) {
        BlockPos pos = tile.getPos();
        player.openGui(AppEngBase.instance(), GuiIds.getGuiId(bridge, returnedFromSubScreen), tile.getWorld(),
            pos.getX(), pos.getY(), pos.getZ());
    }

    public static void openPartGui(EntityPlayer player, GuiIds.GuiKey key, AEBasePart part) {
        openPartGui(player, key, part, false);
    }

    public static void openPartGui(EntityPlayer player, GuiIds.GuiKey key, AEBasePart part,
                                   boolean returnedFromSubScreen) {
        BlockPos pos = part.getHost().getLocation().getPos();
        int side = encodePartSide(Objects.requireNonNull(part.getSide(), "Part GUI requires a sided part"));
        player.openGui(AppEngBase.instance(), GuiIds.getGuiId(key, returnedFromSubScreen), player.world, pos.getX(),
            (side << 8) | (pos.getY() & 255), pos.getZ());
    }

    public static void openPartGui(EntityPlayer player, GuiIds.GuiKey key, IPartHost host, @Nullable EnumFacing side) {
        openPartGui(player, key, host, side, false);
    }

    public static void openPartGui(EntityPlayer player, GuiIds.GuiKey key, IPartHost host, @Nullable EnumFacing side,
                                   boolean returnedFromSubScreen) {
        BlockPos pos = host.getLocation().getPos();
        player.openGui(AppEngBase.instance(), GuiIds.getGuiId(key, returnedFromSubScreen), player.world, pos.getX(),
            (encodePartSide(side) << 8) | (pos.getY() & 255), pos.getZ());
    }

    public static boolean openItemGui(EntityPlayer player, GuiIds.GuiKey key, ItemGuiHostLocator locator) {
        return openItemGui(player, key, locator, false);
    }

    public static boolean openItemGui(EntityPlayer player, GuiIds.GuiKey key, ItemGuiHostLocator locator,
                                      boolean returnedFromSubScreen) {
        Integer slot = locator.getPlayerInventorySlot();
        if (slot == null) {
            if (key == GuiIds.GuiKey.PATTERN_MODIFIER && locator instanceof BaublesItemLocator baublesLocator) {
                player.openGui(AppEngBase.instance(), GuiIds.getGuiId(key, returnedFromSubScreen), player.world,
                    encodeBaublesPatternModifierLocator(baublesLocator), 0, 0);
                return true;
            }
            return false;
        } else if (key == GuiIds.GuiKey.NETWORK_STATUS) {
            RayTraceResult hitResult = locator.hitResult();
            if (hitResult == null || hitResult.getBlockPos() == null) {
                return false;
            }
            openItemGui(player, key, slot, hitResult.getBlockPos(), returnedFromSubScreen);
            return true;
        }

        if (key == GuiIds.GuiKey.PATTERN_MODIFIER || key == GuiIds.GuiKey.ADVANCED_MEMORY_CARD) {
            RayTraceResult hitResult = locator.hitResult();
            if (hitResult != null && hitResult.getBlockPos() != null) {
                BlockPos pos = hitResult.getBlockPos();
                int packedHit = packHitResult(slot, hitResult);
                player.openGui(AppEngBase.instance(), GuiIds.getGuiId(key, returnedFromSubScreen), player.world,
                    pos.getX(), packedHit | (pos.getY() & 255), pos.getZ());
                return true;
            }
        }

        player.openGui(AppEngBase.instance(), GuiIds.getGuiId(key, returnedFromSubScreen), player.world, slot, 0, 0);
        return true;
    }

    private static int encodeBaublesPatternModifierLocator(BaublesItemLocator locator) {
        return -1 - locator.baubleSlot();
    }

    private static int encodePartSide(@Nullable EnumFacing side) {
        return side == null ? EnumFacing.VALUES.length : side.ordinal();
    }

    public static void openItemGui(EntityPlayer player, GuiIds.GuiKey key, int slot, BlockPos pos,
                                   boolean returnedFromSubScreen) {
        player.openGui(AppEngBase.instance(), GuiIds.getGuiId(key, returnedFromSubScreen), player.world, pos.getX(),
            (slot << 8) | (pos.getY() & 255), pos.getZ());
    }

    private static int packHitResult(int slot, RayTraceResult hitResult) {
        BlockPos pos = hitResult.getBlockPos();
        int side = hitResult.sideHit == null ? 0 : hitResult.sideHit.ordinal();
        int hitX = packHitCoordinate(hitResult.hitVec.x - pos.getX());
        int hitY = packHitCoordinate(hitResult.hitVec.y - pos.getY());
        int hitZ = packHitCoordinate(hitResult.hitVec.z - pos.getZ());
        return ((slot + 1) & 0xFF) << 8
            | (side & 0x7) << 16
            | (hitX & 0xF) << 19
            | (hitY & 0xF) << 23
            | (hitZ & 0xF) << 27;
    }

    private static int packHitCoordinate(double coordinate) {
        return Math.clamp((int) Math.round(coordinate * 15.0), 0, 15);
    }
}
