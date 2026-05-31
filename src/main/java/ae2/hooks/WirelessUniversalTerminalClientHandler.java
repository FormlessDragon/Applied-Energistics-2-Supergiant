package ae2.hooks;

import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.CycleWirelessTerminalPacket;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class WirelessUniversalTerminalClientHandler {
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        int wheel = event.getDwheel();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (wheel == 0 || minecraft.player == null || minecraft.currentScreen != null
            || !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            return;
        }
        if (!(minecraft.player.getHeldItemMainhand().getItem() instanceof WirelessUniversalTerminalItem)
            && !(minecraft.player.getHeldItemOffhand().getItem() instanceof WirelessUniversalTerminalItem)) {
            return;
        }

        InitNetwork.sendToServer(new CycleWirelessTerminalPacket(wheel < 0));
        event.setCanceled(true);
    }
}
