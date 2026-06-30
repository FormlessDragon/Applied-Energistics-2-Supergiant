package ae2.core.gui;

import ae2.container.AEBaseContainer;
import ae2.container.implementations.IPatternAccess;
import ae2.core.AELog;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.RestorePreviousGuiPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PatternContainerGuiReturnContext {
    private static final ThreadLocal<AEBaseContainer> ACTIVE_RETURN_CONTAINER = new ThreadLocal<>();
    private static final Map<UUID, Int2ObjectMap<Container>> EXTERNAL_RETURN_CONTAINERS = new HashMap<>();

    private PatternContainerGuiReturnContext() {
    }

    public static <C extends AEBaseContainer & IPatternAccess> void openFromPatternAccessTerminal(
        EntityPlayer player, C returnContainer, Runnable openAction
    ) {
        if (!(player instanceof EntityPlayerMP serverPlayer)) {
            AELog.warn("Cannot open pattern container GUI for non-server player {}", player);
            return;
        }

        Container previousContainer = serverPlayer.openContainer;
        ACTIVE_RETURN_CONTAINER.set(returnContainer);
        try {
            openAction.run();
        } finally {
            ACTIVE_RETURN_CONTAINER.remove();
        }

        Container openedContainer = serverPlayer.openContainer;
        if (openedContainer == previousContainer) {
            AELog.warn("Pattern container GUI open action did not change the player's open container");
            return;
        }
        attachReturnContainer(serverPlayer, openedContainer, returnContainer);
    }

    public static <C extends AEBaseContainer> C initializeContainer(C container) {
        AEBaseContainer returnContainer = ACTIVE_RETURN_CONTAINER.get();
        if (returnContainer != null) {
            container.setReturnToContainerOverride(returnContainer);
            container.setExternalGuiReturn(true);
        }
        return container;
    }

    public static boolean restoreExternalContainer(EntityPlayerMP player, IContainerListener listener) {
        Container currentContainer = player.openContainer;
        Container previousContainer = removeExternalReturnContainer(player, currentContainer.windowId);
        if (previousContainer == null) {
            return false;
        }
        if (!previousContainer.canInteractWith(player)) {
            AELog.warn("Cannot restore PAT container from external pattern container GUI: return container is invalid");
            return false;
        }

        player.getNextWindowId();
        player.closeContainer();
        int windowId = player.currentWindowId;
        player.openContainer = previousContainer;
        player.openContainer.windowId = windowId;
        InitNetwork.CHANNEL.sendTo(new RestorePreviousGuiPacket(windowId), player);
        rebindRestoredContainerListener(player.openContainer, listener);
        return true;
    }

    public static boolean isExternalPatternContainerWindow(EntityPlayerMP player, int windowId) {
        Int2ObjectMap<Container> containersByWindowId = EXTERNAL_RETURN_CONTAINERS.get(playerUuid(player));
        return containersByWindowId != null && containersByWindowId.containsKey(windowId);
    }

    private static <C extends AEBaseContainer & IPatternAccess> void attachReturnContainer(EntityPlayerMP player, @Nullable Container openedContainer,
                                               C returnContainer) {
        if (openedContainer == null) {
            AELog.warn("Pattern container GUI open action cleared the player's open container");
            return;
        }
        if (openedContainer instanceof AEBaseContainer aeContainer) {
            aeContainer.setReturnToContainerOverride(returnContainer);
            aeContainer.setExternalGuiReturn(true);
            return;
        }
        EXTERNAL_RETURN_CONTAINERS.computeIfAbsent(playerUuid(player), ignored -> new Int2ObjectOpenHashMap<>())
                                  .put(openedContainer.windowId, returnContainer);
    }

    private static void rebindRestoredContainerListener(Container container, IContainerListener listener) {
        container.removeListener(listener);
        container.addListener(listener);
    }

    private static UUID playerUuid(EntityPlayerMP player) {
        return player.getUniqueID();
    }

    @Nullable
    private static Container removeExternalReturnContainer(EntityPlayerMP player, int windowId) {
        UUID playerUuid = playerUuid(player);
        Int2ObjectMap<Container> containersByWindowId = EXTERNAL_RETURN_CONTAINERS.get(playerUuid);
        if (containersByWindowId == null) {
            return null;
        }

        Container previousContainer = containersByWindowId.remove(windowId);
        if (containersByWindowId.isEmpty()) {
            EXTERNAL_RETURN_CONTAINERS.remove(playerUuid);
        }
        return previousContainer;
    }
}
