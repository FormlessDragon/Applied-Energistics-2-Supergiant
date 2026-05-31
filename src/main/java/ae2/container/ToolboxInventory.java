package ae2.container;

import ae2.container.slot.RestrictedInputSlot;
import ae2.items.contents.NetworkToolGuiHost;
import ae2.items.tools.NetworkToolItem;
import ae2.text.TextComponentItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/**
 * Helper class for dealing with an equipped toolbox.
 */
public class ToolboxInventory {
    private final AEBaseContainer container;
    private final NetworkToolGuiHost<?> inv;

    public ToolboxInventory(AEBaseContainer container) {
        this.container = container;

        this.inv = NetworkToolItem.findNetworkToolInv(container.getPlayer());
        if (this.inv != null) {
            var playerSlot = this.inv.getPlayerInventorySlot();
            if (playerSlot != null) {
                container.lockPlayerInventorySlot(playerSlot);
            }

            var upgradeCardInv = this.inv.getInventory();
            for (int i = 0; i < upgradeCardInv.size(); i++) {
                var slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgradeCardInv, i);
                container.addSlot(slot, SlotSemantics.TOOLBOX);
            }
        }
    }

    public boolean isPresent() {
        return this.inv != null;
    }

    public void tick() {
        if (this.inv != null) {
            if (!this.inv.isValid()) {
                this.container.setValidContainer(false);
                return;
            }
            this.inv.tick();
        }
    }

    public ITextComponent getName() {
        return this.inv != null ? TextComponentItemStack.of(this.inv.getItemStack()) : new TextComponentString("");
    }
}
