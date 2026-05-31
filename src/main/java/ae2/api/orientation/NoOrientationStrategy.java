package ae2.api.orientation;

import net.minecraft.block.properties.IProperty;

import java.util.Collection;
import java.util.Collections;

class NoOrientationStrategy implements IOrientationStrategy {
    @Override
    public Collection<IProperty<?>> getProperties() {
        return Collections.emptyList();
    }
}
