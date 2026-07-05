package ae2.client.gui.me.common;

import ae2.api.client.terminalsettings.TerminalSettingsPageProvider;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TerminalSettingsPageRegistry {
    private static final Map<ResourceLocation, TerminalSettingsPageProvider> PAGES = new Object2ObjectLinkedOpenHashMap<>();
    private static List<TerminalSettingsPageProvider> cachedRegistered = List.of();
    private static boolean initialized;

    private TerminalSettingsPageRegistry() {
    }

    public static synchronized void register(TerminalSettingsPageProvider provider) {
        ensureClientSide();
        ensureInitialized();
        addPage(provider);
    }

    public static synchronized List<TerminalSettingsPageProvider> getRegistered() {
        ensureClientSide();
        ensureInitialized();
        return cachedRegistered;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        for (TerminalSettingsPageProvider provider : DefaultTerminalSettingsPages.getDefaults()) {
            addPage(provider);
        }
    }

    private static void addPage(TerminalSettingsPageProvider provider) {
        Objects.requireNonNull(provider, "provider");
        ResourceLocation id = Objects.requireNonNull(provider.id(), "providerId");
        if (PAGES.putIfAbsent(id, provider) != null) {
            throw new IllegalArgumentException("Duplicate terminal settings page registration: " + id);
        }
        cachedRegistered = Collections.unmodifiableList(new ObjectArrayList<>(PAGES.values()));
    }

    private static void ensureClientSide() {
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT) {
            throw new IllegalStateException(
                "Terminal settings pages are client-only. Register and query them from client-side code only.");
        }
    }
}
