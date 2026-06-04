package ae2.core.network.clientbound;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.client.gui.me.crafting.GuiCraftingTree;
import ae2.core.network.ClientboundPacket;
import ae2.crafting.CraftingTreeNode;
import ae2.integration.data.CraftingTreeStackRegistry;
import ae2.integration.data.LiteCraftTreeNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class CraftingTreeDataPacket extends ClientboundPacket {

    private LiteCraftTreeNode root = null;

    public CraftingTreeDataPacket() {
    }

    public CraftingTreeDataPacket(final CraftingTreeNode root, final GenericStack output,
                                  Object2LongMap<IPatternDetails> patternTimes, KeyCounter usedItems,
                                  KeyCounter missingItems) {
        this.root = LiteCraftTreeNode.of(root, null, output.amount(),
            new LiteCraftTreeNode.PatternTimesAllocator(patternTimes, usedItems, missingItems));
    }

    @Override
    protected void read(final ByteBuf buf) {
        CraftingTreeStackRegistry stackSet = new CraftingTreeStackRegistry();
        stackSet.read(buf);
        this.root = LiteCraftTreeNode.fromBuffer(buf, stackSet, null);
    }

    @Override
    protected void write(final ByteBuf buf) {
        if (this.root == null) {
            throw new IllegalStateException("Cannot write a crafting tree packet without a root node");
        }

        CraftingTreeStackRegistry stackSet = new CraftingTreeStackRegistry();
        ByteBuf buffer = Unpooled.buffer();
        this.root.writeToBuffer(buffer, stackSet);

        stackSet.write(buf);
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
