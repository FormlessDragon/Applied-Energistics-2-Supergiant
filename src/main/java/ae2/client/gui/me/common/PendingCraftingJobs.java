package ae2.client.gui.me.common;

import ae2.api.client.AEKeyRendering;
import ae2.api.implementations.items.IAEItemPowerStorage;
import ae2.api.stacks.AEKey;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.network.clientbound.CraftingJobStatusPacket;
import ae2.items.tools.powered.WirelessTerminals;
import ae2.util.SearchInventoryEvent;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Map;
import java.util.UUID;

/**
 * Tracks pending crafting jobs started by this player.
 */
@SideOnly(Side.CLIENT)
public final class PendingCraftingJobs {
    private static final Map<UUID, PendingJob> jobs = new Object2ObjectOpenHashMap<>();

    private PendingCraftingJobs() {
    }

    public static boolean hasPendingJob(AEKey what) {
        return jobs.entrySet().stream().anyMatch(s -> s.getValue().what().equals(what));
    }

    public static void clearPendingJobs() {
        jobs.clear();
    }

    public static void jobStatus(UUID id,
                                 AEKey what,
                                 long requestedAmount,
                                 long remainingAmount,
                                 CraftingJobStatusPacket.Status status) {

        AELog.debug("Crafting job " + id + " for " + requestedAmount
            + "x" + AEKeyRendering.getDisplayName(what).getFormattedText() + ". State=" + status);

        PendingJob existing = jobs.get(id);
        switch (status) {
            case STARTED -> {
                if (existing == null) {
                    jobs.put(id, new PendingJob(id, what, requestedAmount, remainingAmount));
                }
            }
            case CANCELLED -> jobs.remove(id);
            case FINISHED -> {
                jobs.remove(id);
                Minecraft minecraft = Minecraft.getMinecraft();
                if (AEConfig.instance().isNotifyForFinishedCraftingJobs()
                    && minecraft.player != null && hasNotificationEnablingItem(minecraft.player)) {
                    minecraft.getToastGui().add(new FinishedJobToast(what, requestedAmount));
                }
            }
            default -> {
            }
        }
    }

    private static boolean hasNotificationEnablingItem(EntityPlayerSP player) {
        for (ItemStack stack : SearchInventoryEvent.getItems(player)) {
            net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
            if (!stack.isEmpty()
                && stack.getItem() instanceof IAEItemPowerStorage
                && ((IAEItemPowerStorage) stack.getItem()).getAECurrentPower(stack) > 0
                && tag != null
                && tag.hasKey(WirelessTerminals.TAG_LINK, 10)) {
                return true;
            }
        }
        return false;
    }

    private record PendingJob(UUID jobId, AEKey what, long requestedAmount, long remainingAmount) {
    }
}
