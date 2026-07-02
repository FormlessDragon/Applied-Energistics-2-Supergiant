package ae2.integration.modules.hei.target;

import ae2.client.gui.AEBaseGui;
import ae2.container.slot.FakeSlot;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.InventoryActionPacket;
import ae2.helpers.InventoryAction;
import mezz.jei.api.gui.IGhostIngredientHandler.Target;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;

public record FakeSlotTarget<T>(AEBaseGui<?> gui, FakeSlot slot) implements Target<T> {
    @Override
    public Rectangle getArea() {
        return new Rectangle(this.gui.getGuiLeft() + this.slot.xPos, this.gui.getGuiTop() + this.slot.yPos, 16, 16);
    }

    @Override
    public void accept(@NotNull T ingredient) {
        if (!this.slot.isEnabled()) {
            return;
        }

        ItemStack stack = HeiGhostTargetSupport.toPacketFilterStack(ingredient);
        if (stack.isEmpty()) {
            return;
        }

        InitNetwork.sendToServer(new InventoryActionPacket(
            this.gui.getContainer().windowId,
            InventoryAction.SET_FILTER,
            this.slot.slotNumber,
            Mouse.getEventButton(),
            stack));
    }
}
