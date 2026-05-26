package appeng.container.implementations;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.storage.ITerminalHost;
import appeng.container.AEBaseContainer;
import net.minecraft.entity.player.InventoryPlayer;

import java.util.concurrent.Future;

public class ContainerCraftingTree extends AEBaseContainer {

    private Future<ICraftingPlan> job = null;

    public ContainerCraftingTree(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    public void setJob(final Future<ICraftingPlan> job) {
        this.job = job;
    }

    public Future<ICraftingPlan> getJob() {
        return job;
    }

}
