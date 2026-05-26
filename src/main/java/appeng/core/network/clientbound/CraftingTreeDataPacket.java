package appeng.core.network.clientbound;

import appeng.client.ctl.gui.GuiCraftingTree;
import appeng.api.stacks.GenericStack;
import appeng.core.network.ClientboundPacket;
import appeng.crafting.CraftingTreeNode;
import appeng.integration.data.LiteCraftTreeNode;
import appeng.util.ctl.AEItemStackSet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class CraftingTreeDataPacket extends ClientboundPacket {

    private LiteCraftTreeNode root = null;

    public CraftingTreeDataPacket() {
    }

    public CraftingTreeDataPacket(final CraftingTreeNode root, final GenericStack output) {
        this.root = LiteCraftTreeNode.of(root, null, output.amount());
    }

    @Override
    protected void read(final ByteBuf buf) {
        AEItemStackSet stackSet = new AEItemStackSet();
        stackSet.fromBuffer(buf);
        this.root = LiteCraftTreeNode.fromBuffer(buf, stackSet, null);
    }

    @Override
    protected void write(final ByteBuf buf) {
        if (this.root == null) {
            throw new IllegalStateException("Cannot write a crafting tree packet without a root node");
        }

        AEItemStackSet stackSet = new AEItemStackSet();
        ByteBuf buffer = Unpooled.buffer();
        this.root.writeToBuffer(buffer, stackSet);

        stackSet.writeToBuffer(buf);
        buf.writeBytes(buffer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (this.root == null) {
            return;
        }

        GuiScreen cur = minecraft.currentScreen;
        if (!(cur instanceof GuiCraftingTree treeGUI)) {
            return;
        }

        treeGUI.onDataUpdate(this.root);
    }

}
