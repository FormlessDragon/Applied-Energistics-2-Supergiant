package ae2.client.gui;

import ae2.container.AEBaseContainer;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.SwitchGuisPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public enum PatternContainerExternalGuiReturnHandler {
    INSTANCE;

    private boolean waitingForPatternContainerGui;
    private int externalPatternContainerWindowId = Integer.MIN_VALUE;

    public void expectPatternContainerGui() {
        this.waitingForPatternContainerGui = true;
        this.externalPatternContainerWindowId = Integer.MIN_VALUE;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!this.waitingForPatternContainerGui) {
            return;
        }
        this.waitingForPatternContainerGui = false;

        GuiScreen screen = event.getGui();
        if (!(screen instanceof GuiContainer guiContainer)) {
            return;
        }
        Container container = guiContainer.inventorySlots;
        if (screen instanceof AEBaseGui<?> aeGui) {
            aeGui.getContainer().setExternalGuiReturn(true);
            return;
        }
        if (container instanceof AEBaseContainer) {
            return;
        }
        this.externalPatternContainerWindowId = container.windowId;
    }

    @SubscribeEvent
    public void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!Keyboard.getEventKeyState()) {
            return;
        }

        GuiScreen screen = event.getGui();
        if (!(screen instanceof GuiContainer guiContainer) || screen instanceof AEBaseGui<?>) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) {
            return;
        }

        Container container = guiContainer.inventorySlots;
        if (container instanceof AEBaseContainer || container != minecraft.player.openContainer
            || container.windowId != this.externalPatternContainerWindowId) {
            return;
        }

        int keyCode = Keyboard.getEventKey();
        if (keyCode != Keyboard.KEY_ESCAPE && !minecraft.gameSettings.keyBindInventory.isActiveAndMatches(keyCode)) {
            return;
        }

        this.externalPatternContainerWindowId = Integer.MIN_VALUE;
        InitNetwork.sendToServer(SwitchGuisPacket.returnToParentGui());
        event.setCanceled(true);
    }
}
