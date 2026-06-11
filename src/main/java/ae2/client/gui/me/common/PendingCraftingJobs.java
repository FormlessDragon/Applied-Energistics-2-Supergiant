package ae2.client.gui.me.common;

import ae2.api.client.AEKeyRendering;
import ae2.api.implementations.items.IAEItemPowerStorage;
import ae2.api.stacks.AEKey;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.core.network.clientbound.CraftingJobStatusPacket;
import ae2.items.tools.powered.WirelessTerminals;
import ae2.util.SearchInventoryEvent;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
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
    private static final Object2IntMap<AEKey> pendingJobCounts = new Object2IntOpenHashMap<>();

    private PendingCraftingJobs() {
    }

    public static boolean hasPendingJob(AEKey what) {
        return pendingJobCounts.containsKey(what);
    }

    public static void clearPendingJobs() {
        jobs.clear();
        pendingJobCounts.clear();
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
                    pendingJobCounts.put(what, pendingJobCounts.getInt(what) + 1);
                }
            }
            case CANCELLED -> removeJob(id);
            case FINISHED -> {
                removeJob(id);
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

    private static void removeJob(UUID id) {
        PendingJob removed = jobs.remove(id);
        if (removed == null) {
            return;
        }

        AEKey what = removed.what();
        int count = pendingJobCounts.getInt(what) - 1;
        if (count > 0) {
            pendingJobCounts.put(what, count);
        } else {
            pendingJobCounts.removeInt(what);
        }
    }

    private static boolean hasNotificationEnablingItem(EntityPlayerSP player) {
        for (ItemStack stack : SearchInventoryEvent.getItems(player)) {
            NBTTagCompound tag = stack.getTagCompound();
            if (!stack.isEmpty()
                && stack.getItem() instanceof IAEItemPowerStorage s
                && s.getAECurrentPower(stack) > 0
                && tag != null
                && tag.hasKey(WirelessTerminals.TAG_LINK, Constants.NBT.TAG_COMPOUND)) {
                return true;
            }
        }
        return false;
    }

    private record PendingJob(UUID jobId, AEKey what, long requestedAmount, long remainingAmount) {
    }
}
