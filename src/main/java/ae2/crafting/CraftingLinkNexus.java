package ae2.crafting;

import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.me.service.CraftingService;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CraftingLinkNexus {

    private final UUID craftId;
    private boolean canceled = false;
    private boolean done = false;
    private int tickOfDeath = 0;
    @Nullable
    private CraftingLink req;
    @Nullable
    private CraftingLink cpu;

    public CraftingLinkNexus(UUID craftId) {
        this.craftId = craftId;
    }

    public boolean isDead(IGrid grid, CraftingService craftingService) {
        if (this.canceled || this.done) {
            return true;
        }

        if (this.getRequest() == null || this.cpu == null) {
            this.tickOfDeath++;
        } else {
            final boolean hasCpu = craftingService.hasCpu(this.cpu.getCpu());
            var requester = this.getRequest().getRequester();
            var actionableNode = requester == null ? null : requester.getActionableNode();
            final boolean hasMachine = actionableNode != null && actionableNode.grid() == grid;

            if (hasCpu && hasMachine) {
                this.tickOfDeath = 0;
            } else {
                this.tickOfDeath += 60;
            }
        }

        if (this.tickOfDeath > 60) {
            this.cancel();
            return true;
        }

        return false;
    }

    void cancel() {
        this.canceled = true;

        if (this.getRequest() != null) {
            this.getRequest().setCanceled(true);
            if (this.getRequest().getRequester() != null) {
                this.getRequest().getRequester().jobStateChange(this.getRequest());
            }
        }

        if (this.cpu != null) {
            this.cpu.setCanceled(true);
        }
    }

    void remove(CraftingLink craftingLink) {
        if (this.getRequest() == craftingLink) {
            this.setRequest(null);
        } else if (this.cpu == craftingLink) {
            this.cpu = null;
        }
    }

    void add(CraftingLink craftingLink) {
        if (craftingLink.getCpu() != null) {
            this.cpu = craftingLink;
        } else if (craftingLink.getRequester() != null) {
            this.setRequest(craftingLink);
        }
    }

    boolean isCanceled() {
        return this.canceled;
    }

    boolean isDone() {
        return this.done;
    }

    void markDone() {
        this.done = true;

        if (this.getRequest() != null) {
            this.getRequest().setDone(true);
            if (this.getRequest().getRequester() != null) {
                this.getRequest().getRequester().jobStateChange(this.getRequest());
            }
        }

        if (this.cpu != null) {
            this.cpu.setDone(true);
        }
    }

    public boolean isRequester(ICraftingRequester requester) {
        return req != null && req.getRequester() == requester;
    }

    public void removeNode() {
        if (this.getRequest() != null) {
            this.getRequest().setNexus(null);
        }

        this.setRequest(null);
        this.tickOfDeath = 0;
    }

    @Nullable
    public CraftingLink getRequest() {
        return this.req;
    }

    public void setRequest(CraftingLink req) {
        this.req = req;
    }

    @Override
    public String toString() {
        return "CraftingLinkNexus{" + this.craftId + "}";
    }
}
