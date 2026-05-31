package ae2.core.network.clientbound;

import ae2.client.gui.me.patternaccess.GuiPatternAccessTerm;
import ae2.core.network.ClientboundPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ClearPatternAccessTerminalPacket extends ClientboundPacket {

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        if (minecraft.currentScreen instanceof GuiPatternAccessTerm) {
            ((GuiPatternAccessTerm<?>) minecraft.currentScreen).clear();
        }
    }
}
