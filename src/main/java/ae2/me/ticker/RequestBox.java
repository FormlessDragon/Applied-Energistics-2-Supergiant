package ae2.me.ticker;

import ae2.api.networking.IGridNode;
import ae2.core.network.InitNetwork;
import ae2.me.InWorldGridNode;
import ae2.core.network.clientbound.ProfileDataUpdatePacket;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class RequestBox {
    private static final Reference2ObjectMap<EntityPlayer, ProfilerJob> WAITING = new Reference2ObjectOpenHashMap<>();

    private RequestBox() {
    }

    public static void clear() {
        WAITING.clear();
    }

    public static boolean checkPermission(EntityPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && server.isSinglePlayer() || player.canUseCommand(2, "ae2_tick_profile");
    }

    public static RespondCode requestProfile(EntityPlayer player, int duration) {
        if (WAITING.containsKey(player)) {
            return RespondCode.WAIT;
        }
        if (!checkPermission(player)) {
            return RespondCode.DENY;
        }
        WAITING.put(player, new ProfilerJob(Math.max(1, duration) * 1_000_000_000L));
        return RespondCode.OK;
    }

    public static boolean cancelProfile(EntityPlayer player) {
        return WAITING.remove(player) != null;
    }

    public static boolean hasJob() {
        return !WAITING.isEmpty();
    }

    public static void acceptTick(long ns, long tick, IGridNode node) {
        if (WAITING.isEmpty() || ns <= 0L || tick <= 0L || !(node instanceof InWorldGridNode inWorldNode)) {
            return;
        }
        if (node.getLevel() == null || node.getLevel().isRemote) {
            return;
        }
        var pos = new ProfilerJob.SamplePos(node.getLevel().provider.getDimension(), inWorldNode.getLocation());
        for (ProfilerJob job : WAITING.values()) {
            job.tick(pos, ns, tick);
        }
    }

    @SubscribeEvent
    public static void check(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || WAITING.isEmpty()) {
            return;
        }
        var finished = new ObjectArrayList<EntityPlayer>();
        for (var entry : WAITING.entrySet()) {
            if (entry.getValue().isFinished()) {
                finished.add(entry.getKey());
                sendData(entry.getKey(), entry.getValue().generateData());
            }
        }
        for (EntityPlayer player : finished) {
            WAITING.remove(player);
        }
    }

    @SubscribeEvent
    public static void playerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WAITING.remove(event.player);
    }

    private static void sendData(EntityPlayer player, ProfileData data) {
        if (player instanceof EntityPlayerMP serverPlayer) {
            InitNetwork.sendToClient(serverPlayer, new ProfileDataUpdatePacket(data));
            player.sendMessage(new TextComponentTranslation("chat.ae2.tick_analyser.finish"));
        }
    }

    public enum RespondCode {
        OK,
        WAIT,
        DENY
    }
}
