package ae2.client.gui.style;

import ae2.client.gui.Icon;
import ae2.core.AppEng;
import ae2.core.AppEngBase;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

@SuppressWarnings("deprecation")
public final class IconAtlas {
    public static final int TEXTURE_WIDTH = 512;
    public static final int TEXTURE_HEIGHT = 512;

    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/generated/states.png");
    private static final Map<Icon, Position> positions = new Object2ObjectOpenHashMap<>();
    private static ResourceLocation texture;
    private static long registryVersion = -1;

    private IconAtlas() {
    }

    public static ResourceLocation getTexture() {
        if (needsRebuild()) {
            rebuild(Minecraft.getMinecraft().getResourceManager());
        }
        return texture;
    }

    public static Blitter getBlitter(Icon icon) {
        if (needsRebuild()) {
            rebuild(Minecraft.getMinecraft().getResourceManager());
        }

        Position position = positions.get(icon);
        if (position == null) {
            rebuild(Minecraft.getMinecraft().getResourceManager());
            position = positions.get(icon);
            if (position == null) {
                throw new IllegalStateException("Missing GUI icon atlas position for " + icon.id());
            }
        }

        return Blitter.texture(texture, TEXTURE_WIDTH, TEXTURE_HEIGHT)
                      .src(position.x(), position.y(), icon.width, icon.height);
    }

    public static void initialize(IResourceManager resourceManager) {
        if (resourceManager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) resourceManager).registerReloadListener(new ReloadListener());
        }
        rebuild(resourceManager);
    }

    public static void invalidate() {
        texture = null;
        positions.clear();
        registryVersion = -1;
    }

    private static void rebuild(IResourceManager resourceManager) {
        BufferedImage atlas = new BufferedImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        positions.clear();
        long rebuiltRegistryVersion = Icon.getRegistryVersion();

        try {
            int x = 0;
            int y = 0;
            int rowHeight = 0;

            for (Icon icon : Icon.getRegisteredIcons()) {
                validateIconSize(icon);
                BufferedImage image = readIcon(resourceManager, icon);
                if (x + icon.width > TEXTURE_WIDTH) {
                    y += rowHeight;
                    x = 0;
                    rowHeight = 0;
                }
                if (y + icon.height > TEXTURE_HEIGHT) {
                    throw new IllegalStateException("GUI icon atlas is too small for " + icon.id());
                }

                graphics.drawImage(image, x, y, icon.width, icon.height, null);
                positions.put(icon, new Position(x, y));
                x += icon.width;
                rowHeight = Math.max(rowHeight, icon.height);
            }
        } finally {
            graphics.dispose();
        }

        Minecraft.getMinecraft().getTextureManager().loadTexture(TEXTURE, new DynamicTexture(atlas));
        texture = TEXTURE;
        registryVersion = rebuiltRegistryVersion;
    }

    private static boolean needsRebuild() {
        return texture == null || registryVersion != Icon.getRegistryVersion();
    }

    private static void validateIconSize(Icon icon) {
        if (!icon.hasResolvedSize()) {
            AppEngBase.LOGGER.error("GUI icon {} has unresolved size {}x{}",
                icon.id(), icon.width, icon.height);
            throw new IllegalStateException("GUI icon " + icon.id() + " has unresolved size "
                + icon.width + "x" + icon.height + ". Automatic icon sizes must be resolved during client registration.");
        }
    }

    private static BufferedImage readIcon(IResourceManager resourceManager, Icon icon) {
        ResourceLocation location = icon.texture();
        try (var input = resourceManager.getResource(location).getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalStateException("Failed to read icon image " + location);
            }
            return image;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load GUI icon " + location, e);
        }
    }

    private record Position(int x, int y) {
    }

    private static class ReloadListener implements IResourceManagerReloadListener {
        @Override
        public void onResourceManagerReload(IResourceManager resourceManager) {
            rebuild(resourceManager);
        }
    }
}
