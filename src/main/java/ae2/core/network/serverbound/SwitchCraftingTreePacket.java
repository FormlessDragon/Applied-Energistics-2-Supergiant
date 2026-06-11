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
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import java.util.concurrent.FutureTask;

public class SwitchCraftingTreePacket extends ServerboundPacket {
    private int windowId;

    public SwitchCraftingTreePacket() {
    }

    public SwitchCraftingTreePacket(int windowId) {
        this.windowId = windowId;
    }

    @Override
    protected void read(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.windowId = packetBuffer.readVarInt();
        if (packetBuffer.isReadable()) {
            throw new IllegalArgumentException("Trailing crafting tree switch packet payload bytes: "
                + packetBuffer.readableBytes());
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        new PacketBuffer(buf).writeVarInt(this.windowId);
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
        InitNetwork.sendToClient(player,
            new CraftingTreeDataPacket(tree, job.finalOutput(), job.patternTimes(), job.usedItems(),
                job.missingItems()));
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerCraftConfirm confirm) {
            if (confirm.windowId != this.windowId) {
                return;
            }
            processConfirmGUI(confirm, player);
        } else if (player.openContainer instanceof ContainerCraftingTree craftingTree) {
            if (craftingTree.windowId != this.windowId) {
                return;
            }
            processTreeGUI(craftingTree, player);
        }
    }

}
