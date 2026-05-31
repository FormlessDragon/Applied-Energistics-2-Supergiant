package ae2.core;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

import java.util.Map;
import java.util.WeakHashMap;

@SuppressWarnings("deprecation")
public final class PlayerState {

    private static final PlayerState INSTANCE = new PlayerState();

    private final Map<EntityPlayer, Boolean> holdingCtrl = new WeakHashMap<>();
    private boolean registered;

    private PlayerState() {
    }

    public static void init() {
        INSTANCE.register();
    }

    public static boolean isHoldingCtrl(EntityPlayer player) {
        synchronized (INSTANCE.holdingCtrl) {
            return INSTANCE.holdingCtrl.getOrDefault(player, Boolean.FALSE);
        }
    }

    public static void setHoldingCtrl(EntityPlayer player, boolean holdingCtrl) {
        synchronized (INSTANCE.holdingCtrl) {
            if (holdingCtrl) {
                INSTANCE.holdingCtrl.put(player, Boolean.TRUE);
            } else {
                INSTANCE.holdingCtrl.remove(player);
            }
        }
    }

    private void register() {
        if (this.registered) {
            return;
        }
        this.registered = true;
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    private void clear(EntityPlayer player) {
        synchronized (this.holdingCtrl) {
            this.holdingCtrl.remove(player);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        setHoldingCtrl(event.getEntityPlayer(), isHoldingCtrl(event.getOriginal()));
        clear(event.getOriginal());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        clear(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        clear(event.player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        clear(event.player);
    }
}
