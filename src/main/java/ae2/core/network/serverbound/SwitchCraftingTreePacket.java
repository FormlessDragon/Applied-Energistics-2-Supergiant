package ae2.core.network.serverbound;

import ae2.api.networking.crafting.ICraftingPlan;
import ae2.container.GuiIds;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.container.implementations.ContainerCraftingTree;
import ae2.core.AppEngBase;
import ae2.core.network.InitNetwork;
import ae2.core.network.ServerboundPacket;
import ae2.core.network.clientbound.CraftingTreeDataPacket;
import ae2.crafting.CraftingPlan;
import ae2.crafting.CraftingTreeNode;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.concurrent.FutureTask;

public class SwitchCraftingTreePacket extends ServerboundPacket {

    public SwitchCraftingTreePacket() {
    }

    private static void processTreeGUI(final ContainerCraftingTree craftingTree, final EntityPlayerMP player) {
        var locator = craftingTree.getLocator();
        if (locator == null) {
            return;
        }

        if (!SwitchGuisPacket.openSubGui(player, locator, GuiIds.GuiKey.CRAFT_CONFIRM)) {
            return;
        }

        if (player.openContainer instanceof ContainerCraftConfirm confirm) {
            confirm.setJob(craftingTree.getJob());
            confirm.detectAndSendChanges();
        }
    }

    private static void processConfirmGUI(final ContainerCraftConfirm confirm, final EntityPlayerMP player) {
        ICraftingPlan result = confirm.getResult();
        if (result == null) {
            return;
        }

        if (!(result instanceof CraftingPlan job)) {
            return;
        }

        CraftingTreeNode tree = job.tree();
        if (tree == null) {
            return;
        }

        player.openGui(AppEngBase.instance(), GuiIds.GuiKey.CRAFTING_TREE.getGuiId(), player.world,
            (int) player.posX, (int) player.posY, (int) player.posZ);
        if (!(player.openContainer instanceof ContainerCraftingTree craftingTree)) {
            return;
        }

        FutureTask<ICraftingPlan> fakeFuture = new FutureTask<>(() -> result);
        craftingTree.setJob(fakeFuture);
        fakeFuture.run();
        InitNetwork.sendToClient(player, new CraftingTreeDataPacket(tree, job.finalOutput()));
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerCraftConfirm confirm) {
            processConfirmGUI(confirm, player);
        } else if (player.openContainer instanceof ContainerCraftingTree craftingTree) {
            processTreeGUI(craftingTree, player);
        }
    }

}
