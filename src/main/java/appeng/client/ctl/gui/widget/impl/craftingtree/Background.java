package appeng.client.ctl.gui.widget.impl.craftingtree;

import appeng.client.ctl.gui.util.TextureProperties;
import appeng.core.AppEng;
import net.minecraft.util.ResourceLocation;

public enum Background {

    BG_256_256_LIGHT(
            TextureProperties.of(
                    new ResourceLocation(AppEng.MOD_ID, "textures/guis/ctl/guicraftingtree_256x256_light.png"),
                    0, 0, 256, 256
            ), false
    ),
    BG_256_256_DARK(
            TextureProperties.of(
                    new ResourceLocation(AppEng.MOD_ID, "textures/guis/ctl/guicraftingtree_256x256_dark.png"),
                    0, 0, 256, 256
            ), true
    ),
    BG_320_256_LIGHT(
            TextureProperties.of(
                    new ResourceLocation(AppEng.MOD_ID, "textures/guis/ctl/guicraftingtree_320x256_light.png"),
                    0, 0, 320, 256
            ), false
    ),
    BG_320_256_DARK(
            TextureProperties.of(
                    new ResourceLocation(AppEng.MOD_ID, "textures/guis/ctl/guicraftingtree_320x256_dark.png"),
                    0, 0, 320, 256
            ), true
    ),
    BG_384_320_LIGHT(
            TextureProperties.of(
                    new ResourceLocation(AppEng.MOD_ID, "textures/guis/ctl/guicraftingtree_384x320_light.png"),
                    0, 0, 384, 320
            ), false
    ),
    BG_384_320_DARK(
            TextureProperties.of(
                    new ResourceLocation(AppEng.MOD_ID, "textures/guis/ctl/guicraftingtree_384x320_dark.png"),
                    0, 0, 384, 320
            ), true
    ),
    BG_512_320_LIGHT(
            TextureProperties.of(
                    new ResourceLocation(AppEng.MOD_ID, "textures/guis/ctl/guicraftingtree_512x320_light.png"),
                    0, 0, 512, 320
            ), false
    ),
    BG_512_320_DARK(
            TextureProperties.of(
                    new ResourceLocation(AppEng.MOD_ID, "textures/guis/ctl/guicraftingtree_512x320_dark.png"),
                    0, 0, 512, 320
            ), true
    ),
    BG_640_384_LIGHT(
            TextureProperties.of(
                    new ResourceLocation(AppEng.MOD_ID, "textures/guis/ctl/guicraftingtree_640x384_light.png"),
                    0, 0, 640, 384
            ), false
    ),
    BG_640_384_DARK(
            TextureProperties.of(
                    new ResourceLocation(AppEng.MOD_ID, "textures/guis/ctl/guicraftingtree_640x384_dark.png"),
                    0, 0, 640, 384
            ), true
    ),
    ;

    private final TextureProperties texture;
    private final int internalWidth;
    private final int internalHeight;
    private final int internalXOffset;
    private final int internalYOffset;
    private final boolean dark;

    Background(final TextureProperties texture, final boolean dark) {
        this.texture = texture;
        this.dark = dark;
        this.internalWidth = texture.width() - (7 * 2);
        this.internalHeight = texture.height() - (25 + 9);
        this.internalXOffset = 7;
        this.internalYOffset = 25;
    }

    public TextureProperties getTexture() {
        return texture;
    }

    public int getInternalWidth() {
        return internalWidth;
    }

    public int getInternalHeight() {
        return internalHeight;
    }

    public int getInternalXOffset() {
        return internalXOffset;
    }

    public int getInternalYOffset() {
        return internalYOffset;
    }

    public static Background getLargest(final int screenWidth, final int screenHeight, final boolean dark) {
        for (int i = values().length - 1; i >= 0; i--) {
            Background bg = values()[i];
            if (bg.dark != dark) {
                continue;
            }
            if (screenWidth >= bg.texture.width() * 1.25 && screenHeight >= bg.texture.height()) {
                return bg;
            }
        }
        return dark ? BG_256_256_DARK : BG_256_256_LIGHT;
    }

}