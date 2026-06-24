package ae2.client.gui;

import ae2.core.AppEngBase;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

@SideOnly(Side.CLIENT)
final class ClientIconSizeReader {
    private ClientIconSizeReader() {
    }

    static Icon.Size read(ResourceLocation texture) {
        try (var input = Minecraft.getMinecraft().getResourceManager().getResource(texture).getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                AppEngBase.LOGGER.error("Failed to read GUI icon image {}", texture);
                throw new IllegalStateException("Failed to read GUI icon image " + texture);
            }
            if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                AppEngBase.LOGGER.error("GUI icon {} size must be positive, got {}x{}",
                    texture, image.getWidth(), image.getHeight());
                throw new IllegalStateException("GUI icon " + texture + " size must be positive, got "
                    + image.getWidth() + "x" + image.getHeight());
            }
            return new Icon.Size(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            AppEngBase.LOGGER.error("Failed to load GUI icon size {}", texture, e);
            throw new RuntimeException("Failed to load GUI icon size " + texture, e);
        }
    }
}
