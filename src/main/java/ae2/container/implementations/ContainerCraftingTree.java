package ae2.container.implementations;

import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.storage.ITerminalHost;
import ae2.container.AEBaseContainer;
import net.minecraft.entity.player.InventoryPlayer;

import java.util.concurrent.Future;

public class ContainerCraftingTree extends AEBaseContainer {

    private Future<ICraftingPlan> job = null;

    public ContainerCraftingTree(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    public Future<ICraftingPlan> getJob() {
        return job;
    }

    public void setJob(final Future<ICraftingPlan> job) {
        this.job = job;
    }

}
