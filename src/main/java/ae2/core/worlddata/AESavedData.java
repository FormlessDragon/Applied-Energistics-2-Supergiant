package ae2.core.worlddata;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Base class for all AE2 saved data to make them more resistant to crashes while writing. Thank you RS for the idea!
 */
public abstract class AESavedData extends WorldSavedData {
    private static final Logger LOG = LoggerFactory.getLogger(AESavedData.class);

    protected AESavedData() {
        this("");
    }

    protected AESavedData(String name) {
        super(name);
    }

    public void save(File file) {
        if (!this.isDirty()) {
            return;
        }

        var targetPath = file.toPath().toAbsolutePath();
        var tempFile = targetPath.getParent().resolve(file.getName() + ".temp");

        NBTTagCompound compoundTag = new NBTTagCompound();
        compoundTag.setTag("data", this.writeToNBT(new NBTTagCompound()));
        try {
            try (var output = Files.newOutputStream(tempFile)) {
                CompressedStreamTools.writeCompressed(compoundTag, output);
            }
            try {
                Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            this.setDirty(false);
        } catch (IOException e) {
            LOG.error("Could not save data {}", this, e);
        }
    }
}
