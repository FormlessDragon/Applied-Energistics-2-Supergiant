package ae2.crafting;

import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.core.AELog;
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
        finish(true);
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
            applyTerminalState(craftingLink);
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
        finish(false);
    }

    private void finish(boolean canceled) {
        if (this.canceled || this.done) {
            return;
        }
        this.canceled = canceled;
        this.done = !canceled;
        var request = this.req;
        if (request != null) {
            applyTerminalState(request);
        }
        if (this.cpu != null) {
            applyTerminalState(this.cpu);
        }
        notifyRequester(request);
    }

    private void applyTerminalState(CraftingLink link) {
        if (this.canceled) {
            link.setCanceled(true);
        } else if (this.done) {
            link.setDone(true);
        }
    }

    private void notifyRequester(@Nullable CraftingLink request) {
        var requester = request == null ? null : request.getRequester();
        if (requester != null) {
            try {
                requester.jobStateChange(request);
            } catch (RuntimeException failure) {
                AELog.error(failure, "Failed to notify a crafting requester of a terminal link");
            }
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

    public void setRequest(@Nullable CraftingLink req) {
        if (this.req == req) {
            return;
        }
        this.req = req;
        if (req != null && (this.canceled || this.done)) {
            applyTerminalState(req);
            notifyRequester(req);
        }
    }

    @Override
    public String toString() {
        return "CraftingLinkNexus{" + this.craftId + "}";
    }
}
