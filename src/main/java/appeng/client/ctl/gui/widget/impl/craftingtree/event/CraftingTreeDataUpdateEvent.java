package appeng.client.ctl.gui.widget.impl.craftingtree.event;

import appeng.client.ctl.gui.widget.event.GuiEvent;
import appeng.integration.data.LiteCraftTreeNode;

public class CraftingTreeDataUpdateEvent extends GuiEvent {

    private final LiteCraftTreeNode root;

    public CraftingTreeDataUpdateEvent(final LiteCraftTreeNode root) {
        super(null);
        this.root = root;
    }

    public LiteCraftTreeNode getRoot() {
        return root;
    }

}