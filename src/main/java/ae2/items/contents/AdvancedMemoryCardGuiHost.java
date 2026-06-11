package ae2.items.contents;

import ae2.api.implementations.guiobjects.ItemGuiHost;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.items.tools.AdvancedMemoryCardItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class AdvancedMemoryCardGuiHost extends ItemGuiHost<AdvancedMemoryCardItem> {
    @Nullable
    private final IInWorldGridNodeHost gridHost;
    @Nullable
    private final IGridNode clickedGridNode;
    @Nullable
    private final BlockPos clickedPos;
    private final EnumFacing focusedSide;
    private final boolean preferClickedNodeForGrid;

    public AdvancedMemoryCardGuiHost(AdvancedMemoryCardItem item, EntityPlayer player, ItemGuiHostLocator locator,
                                     @Nullable IInWorldGridNodeHost gridHost, @Nullable IGridNode clickedGridNode,
                                     @Nullable BlockPos clickedPos, @Nullable EnumFacing focusedSide,
                                     boolean preferClickedNodeForGrid) {
        super(item, player, locator);
        this.gridHost = gridHost;
        this.clickedGridNode = clickedGridNode;
        this.clickedPos = clickedPos;
        this.focusedSide = focusedSide;
        this.preferClickedNodeForGrid = preferClickedNodeForGrid;
    }

    @Nullable
    public IInWorldGridNodeHost getGridHost() {
        return gridHost;
    }

    @Nullable
    public IGridNode getClickedGridNode() {
        return clickedGridNode;
    }

    @Nullable
    public BlockPos getClickedPos() {
        return clickedPos;
    }

    @Nullable
    public EnumFacing getFocusedSide() {
        return focusedSide;
    }

    public boolean preferClickedNodeForGrid() {
        return preferClickedNodeForGrid;
    }
}
