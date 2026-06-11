package ae2.text;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public final class TextComponents {
    private TextComponents() {
    }

    public static void writeToPacket(PacketBuffer buffer, @Nullable ITextComponent value) {
        buffer.writeBoolean(value != null);
        if (value == null) {
            return;
        }

        buffer.writeBoolean(value instanceof ICustomTextComponent);
        if (value instanceof ICustomTextComponent customTextComponent) {
            buffer.writeString(customTextComponent.getTypeId());
            customTextComponent.writeToPacket(buffer);
            return;
        }

        buffer.writeTextComponent(value);
    }

    @Nullable
    public static ITextComponent readFromPacket(PacketBuffer buffer) {
        try {
            if (!buffer.readBoolean()) {
                return null;
            }

            if (buffer.readBoolean()) {
                return CustomTextComponents.decodePacket(buffer.readString(256), buffer);
            }

            return buffer.readTextComponent();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read text component", e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not read text component packet", e);
        }
    }

    public static String componentKey(@Nullable ITextComponent component) {
        if (component == null) {
            return "null";
        }

        return ITextComponent.Serializer.componentToJson(component);
    }
}
