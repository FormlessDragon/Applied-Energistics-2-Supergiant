package appeng.client.ctl.gui.widget.impl.craftingtree.event;

import appeng.client.ctl.gui.widget.event.GuiEvent;
import appeng.client.ctl.gui.widget.impl.craftingtree.TreeNode;

public class TreeNodeSelectEvent extends GuiEvent {

    private final TreeNode selectedNode;

    public TreeNodeSelectEvent(final TreeNode selectedNode) {
        super(null);
        this.selectedNode = selectedNode;
    }

    public TreeNode getSelectedNode() {
        return selectedNode;
    }

}