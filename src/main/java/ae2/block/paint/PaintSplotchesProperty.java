package ae2.block.paint;

import net.minecraftforge.common.property.IUnlistedProperty;
import org.jspecify.annotations.Nullable;

class PaintSplotchesProperty implements IUnlistedProperty<PaintSplotches> {

    @Override
    public String getName() {
        return "paint_splots";
    }

    @Override
    public boolean isValid(PaintSplotches value) {
        return value != null;
    }

    @Override
    public Class<PaintSplotches> getType() {
        return PaintSplotches.class;
    }

    @Override
    public @Nullable String valueToString(PaintSplotches value) {
        return null;
    }
}

