package ae2.core.network.clientbound;

import ae2.core.network.ClientboundPacket;
import ae2.hooks.CompassManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ClearCompassCachePacket extends ClientboundPacket {

    @Override
    @SideOnly(Side.CLIENT)
    public void handleClient(Minecraft minecraft) {
        CompassManager.INSTANCE.clear();
    }
}
