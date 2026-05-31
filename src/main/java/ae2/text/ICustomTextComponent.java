package ae2.text;

import com.google.gson.JsonObject;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;

public interface ICustomTextComponent extends ITextComponent {

    String getTypeId();

    JsonObject writeJson();

    void writeToPacket(PacketBuffer buffer);
}
