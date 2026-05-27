package appeng.hooks;

import appeng.block.networking.CableBusBlock;
import appeng.core.network.InitNetwork;
import appeng.core.network.serverbound.CableBusPartLeftClickPacket;
import appeng.tile.networking.TileCableBus;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.jspecify.annotations.Nullable;

public final class CableBusLeftClickHook {
    private boolean suppressUntilAttackReleased;

    private static @Nullable Vec3d normalizeLocalHit(BlockPos pos, Vec3d hitVec) {
        if (hitVec == null) {
            return null;
        }
        if (hitVec.x >= 0 && hitVec.x <= 1 && hitVec.y >= 0 && hitVec.y <= 1 && hitVec.z >= 0 && hitVec.z <= 1) {
            return hitVec;
        }
        return hitVec.subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.isCanceled() || event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }

        EntityPlayer player = event.getEntityPlayer();
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (player == null || world == null || pos == null) {
            return;
        }
        if (!world.isRemote) {
            return;
        }

        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof CableBusBlock)) {
            return;
        }

        if (this.suppressUntilAttackReleased) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
            return;
        }

        Vec3d localHit = normalizeLocalHit(pos, event.getHitVec());
        if (localHit == null) {
            return;
        }

        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileCableBus cableBus)) {
            return;
        }

        if (cableBus.onClicked(player, localHit)) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
            this.suppressUntilAttackReleased = true;
            InitNetwork.sendToServer(new CableBusPartLeftClickPacket(pos, localHit));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.world == null || !minecraft.gameSettings.keyBindAttack.isKeyDown()) {
            this.suppressUntilAttackReleased = false;
        }
    }
}
