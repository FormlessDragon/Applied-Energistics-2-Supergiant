package ae2.me.tracker;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

public final class PlayerTracker {
    private static final Reference2ObjectMap<EntityPlayer, Tracker> TRACKERS = new Reference2ObjectOpenHashMap<>();

    private PlayerTracker() {
    }

    public static boolean needUpdate(EntityPlayer player, int dimension, BlockPos pos) {
        long time = player.world.getTotalWorldTime();
        Tracker last = TRACKERS.get(player);
        Tracker current = new Tracker(dimension, pos, time);
        if (last == null || last.dimension != dimension || !last.pos.equals(pos) || time - last.time > 100) {
            TRACKERS.put(player, current);
            return true;
        }
        return false;
    }

    public static void clear() {
        TRACKERS.clear();
    }

    @SubscribeEvent
    public static void playerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        remove(event.player);
    }

    @SubscribeEvent
    public static void playerChangeWorld(PlayerEvent.PlayerChangedDimensionEvent event) {
        remove(event.player);
    }

    @SubscribeEvent
    public static void playerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        remove(event.player);
    }

    private static void remove(EntityPlayer player) {
        TRACKERS.remove(player);
    }

    public record Tracker(int dimension, BlockPos pos, long time) {
    }
}
