package ae2.crafting;

import ae2.api.config.Actionable;
import ae2.api.networking.crafting.ICraftingCPU;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.stacks.AEKey;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public class CraftingLink implements ICraftingLink {

    private final ICraftingRequester req;
    private final ICraftingCPU cpu;
    private final UUID craftId;
    private final boolean standalone;
    private boolean canceled = false;
    private boolean done = false;
    private CraftingLinkNexus tie;

    public CraftingLink(NBTTagCompound data, ICraftingRequester req) {
        this.craftId = data.getUniqueId("craftId");
        this.setCanceled(data.getBoolean("canceled"));
        this.setDone(data.getBoolean("done"));
        this.standalone = data.getBoolean("standalone");

        if (!data.hasKey("req") || !data.getBoolean("req")) {
            throw new IllegalStateException("Invalid Crafting Link for Object");
        }

        this.req = req;
        this.cpu = null;
    }

    public CraftingLink(NBTTagCompound data, ICraftingCPU cpu) {
        this.craftId = data.getUniqueId("craftId");
        this.setCanceled(data.getBoolean("canceled"));
        this.setDone(data.getBoolean("done"));
        this.standalone = data.getBoolean("standalone");

        if (!data.hasKey("req") || data.getBoolean("req")) {
            throw new IllegalStateException("Invalid Crafting Link for Object");
        }

        this.cpu = cpu;
        this.req = null;
    }

    @Override
    public boolean isCanceled() {
        if (this.canceled) {
            return true;
        }

        if (this.done || this.tie == null) {
            return false;
        }

        return this.tie.isCanceled();
    }

    void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    @Override
    public boolean isDone() {
        if (this.done) {
            return true;
        }

        if (this.canceled || this.tie == null) {
            return false;
        }

        return this.tie.isDone();
    }

    void setDone(boolean done) {
        this.done = done;
    }

    @Override
    public void cancel() {
        if (this.done) {
            return;
        }

        this.setCanceled(true);

        if (this.tie != null) {
            this.tie.cancel();
        }

        this.tie = null;
    }

    @Override
    public boolean isStandalone() {
        return this.standalone;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setUniqueId("craftId", this.craftId);
        tag.setBoolean("canceled", this.isCanceled());
        tag.setBoolean("done", this.isDone());
        tag.setBoolean("standalone", this.standalone);
        tag.setBoolean("req", this.getRequester() != null);
    }

    @Override
    public UUID getCraftingID() {
        return this.craftId;
    }

    public void setNexus(CraftingLinkNexus nexus) {
        if (this.tie != null) {
            this.tie.remove(this);
        }

        if (this.isCanceled() && nexus != null) {
            nexus.cancel();
            this.tie = null;
            return;
        }

        this.tie = nexus;

        if (nexus != null) {
            nexus.add(this);
        }
    }

    public long insert(AEKey what, long amount, Actionable mode) {
        if (this.tie == null || this.tie.getRequest() == null || this.tie.getRequest().getRequester() == null) {
            return 0;
        }

        if (this.tie.isCanceled()) {
            return 0;
        }

        return this.tie.getRequest().getRequester().insertCraftedItems(this.tie.getRequest(), what, amount, mode);
    }

    public void markDone() {
        if (this.tie != null) {
            this.tie.markDone();
        }
    }

    ICraftingRequester getRequester() {
        return this.req;
    }

    ICraftingCPU getCpu() {
        return this.cpu;
    }
}
