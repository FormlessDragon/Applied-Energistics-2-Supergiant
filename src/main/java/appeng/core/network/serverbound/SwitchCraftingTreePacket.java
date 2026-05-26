package appeng.core.network.serverbound;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.ContainerCraftingTree;
import appeng.container.GuiIds;
import appeng.core.AppEngBase;
import appeng.core.network.InitNetwork;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.clientbound.CraftingTreeDataPacket;
import appeng.crafting.CraftingPlan;
import appeng.crafting.CraftingTreeNode;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.concurrent.FutureTask;

public class SwitchCraftingTreePacket extends ServerboundPacket {

    public SwitchCraftingTreePacket() {
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerCraftConfirm confirm) {
            processConfirmGUI(confirm, player);
        } else if (player.openContainer instanceof ContainerCraftingTree craftingTree) {
            processTreeGUI(craftingTree, player);
        }
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

}
