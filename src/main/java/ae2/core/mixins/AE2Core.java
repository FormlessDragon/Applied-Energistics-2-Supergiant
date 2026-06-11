package ae2.core.mixins;

import ae2.util.EmptyArrays;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.Nullable;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
@IFMLLoadingPlugin.Name("AE2Core")
@IFMLLoadingPlugin.SortingIndex(-1)
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class AE2Core implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Nullable
    public String[] getASMTransformerClass() {
        return EmptyArrays.EMPTY_STRING_ARRAY;
    }

    @Nullable
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    public String getSetupClass() {
        return null;
    }

    public void injectData(Map<String, Object> data) {
    }

    @Nullable
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("ae2.default.mixins.json");
    }
}
