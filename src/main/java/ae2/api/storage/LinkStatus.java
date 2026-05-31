package ae2.api.storage;

import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

public record LinkStatus(boolean connected, @Nullable ITextComponent statusDescription) implements ILinkStatus {
    static final LinkStatus CONNECTED = new LinkStatus(true, null);
}
